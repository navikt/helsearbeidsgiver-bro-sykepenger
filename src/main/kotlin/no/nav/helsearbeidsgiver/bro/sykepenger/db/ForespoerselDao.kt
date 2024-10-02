package no.nav.helsearbeidsgiver.bro.sykepenger.db

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.BesvarelseMetadataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.zipWithNextOrNull
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDateTime
import java.util.UUID

class ForespoerselDao(
    private val db: Database,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun lagre(forespoersel: ForespoerselDto): Long =
        transaction(db) {
            oppdaterStatuser(
                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                erstattStatuser = setOf(Status.AKTIV),
                nyStatus = Status.FORKASTET,
                activeTransaction = this,
            )

            ForespoerselTable
                .insert {
                    it[forespoerselId] = forespoersel.forespoerselId
                    it[type] = forespoersel.type.name
                    it[status] = forespoersel.status.name
                    it[orgnr] = forespoersel.orgnr.verdi
                    it[fnr] = forespoersel.fnr
                    it[vedtaksperiodeId] = forespoersel.vedtaksperiodeId
                    it[egenmeldingsperioder] = forespoersel.egenmeldingsperioder
                    it[sykmeldingsperioder] = forespoersel.sykmeldingsperioder
                    it[bestemmendeFravaersdager] = forespoersel.bestemmendeFravaersdager
                    it[forespurtData] = forespoersel.forespurtData
                    it[opprettet] = forespoersel.opprettet
                    it[oppdatert] = forespoersel.oppdatert
                }.let {
                    it[ForespoerselTable.id]
                }
        }

    fun oppdaterForespoerslerSomBesvartFraSpleis(
        vedtaksperiodeId: UUID,
        besvart: LocalDateTime,
        inntektsmeldingId: UUID?,
    ): Int =
        transaction(db) {
            val oppdaterteForespoersler =
                oppdaterStatuser(
                    vedtaksperiodeId = vedtaksperiodeId,
                    erstattStatuser = setOf(Status.AKTIV, Status.BESVART_SIMBA, Status.BESVART_SPLEIS),
                    nyStatus = Status.BESVART_SPLEIS,
                    activeTransaction = this,
                )

            oppdaterteForespoersler.forEach { id ->
                insertOrUpdateBesvarelse(id, besvart, inntektsmeldingId, this)
            }

            oppdaterteForespoersler.size
        }

    fun oppdaterForespoerslerSomBesvartFraSimba(
        vedtaksperiodeId: UUID,
        besvart: LocalDateTime,
    ): Int =
        transaction(db) {
            val oppdaterteForespoersler =
                oppdaterStatuser(
                    vedtaksperiodeId = vedtaksperiodeId,
                    erstattStatuser = setOf(Status.AKTIV, Status.BESVART_SIMBA),
                    nyStatus = Status.BESVART_SIMBA,
                    activeTransaction = this,
                )

            oppdaterteForespoersler.forEach { id ->
                insertOrUpdateBesvarelse(id, besvart, null, this)
            }

            oppdaterteForespoersler.size
        }

    fun oppdaterForespoerslerSomForkastet(vedtaksperiodeId: UUID) {
        oppdaterStatuser(
            vedtaksperiodeId = vedtaksperiodeId,
            erstattStatuser = setOf(Status.AKTIV),
            nyStatus = Status.FORKASTET,
        )
    }

    fun hentForespoerselForForespoerselId(forespoerselId: UUID): ForespoerselDto? =
        transaction(db) {
            ForespoerselTable
                .join(
                    BesvarelseTable,
                    JoinType.LEFT,
                    ForespoerselTable.id,
                    BesvarelseTable.fkForespoerselId,
                ).selectAll()
                .where {
                    ForespoerselTable.forespoerselId eq forespoerselId
                }.map(::tilForespoerselDto)
                .firstOrNull()
        }

    fun hentNyesteForespoerselForForespoerselId(
        forespoerselId: UUID,
        statuser: Set<Status>,
    ): ForespoerselDto? =
        hentVedtaksperiodeId(forespoerselId)?.let { vedtaksperiodeId ->
            hentForespoerslerForVedtaksperiodeId(
                vedtaksperiodeId = vedtaksperiodeId,
                statuser = statuser,
            ).maxByOrNull { it.opprettet }
        }

    fun hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId: UUID): ForespoerselDto? =
        hentForespoerslerForVedtaksperiodeId(vedtaksperiodeId, setOf(Status.AKTIV))
            .also { forespoersler ->
                if (forespoersler.size > 1) {
                    "Fant flere aktive forespørsler for vedtaksperiode: $vedtaksperiodeId".also {
                        logger.error(it)
                        sikkerLogger.error(it)
                    }
                }
            }.maxByOrNull { it.opprettet }

    fun hentForespoerslerEksponertTilSimba(vedtaksperiodeIdListe: List<UUID>): List<ForespoerselDto> =
        hentForespoerslerForVedtaksperiodeIdListe(vedtaksperiodeIdListe)
            .groupBy { it.vedtaksperiodeId }
            .mapNotNull { (_, forespoersler) ->
                forespoersler.finnEksponertForespoersel()
            }

    fun hentForespoerslerForVedtaksperiodeId(
        vedtaksperiodeId: UUID,
        statuser: Set<Status>,
    ): List<ForespoerselDto> =
        transaction(db) {
            ForespoerselTable
                .join(
                    BesvarelseTable,
                    JoinType.LEFT,
                    ForespoerselTable.id,
                    BesvarelseTable.fkForespoerselId,
                ).selectAll()
                .where {
                    (ForespoerselTable.vedtaksperiodeId eq vedtaksperiodeId) and
                        (ForespoerselTable.status inList statuser.map { it.name })
                }.map(::tilForespoerselDto)
                .sortedBy { it.opprettet }
        }

    fun hentAktiveForespoerslerForOrgnrOgFnr(
        orgnr: Orgnr,
        fnr: String,
    ): List<ForespoerselDto> =
        transaction(db) {
            ForespoerselTable
                .selectAll()
                .where {
                    (ForespoerselTable.orgnr eq orgnr.verdi) and
                        (ForespoerselTable.fnr eq fnr)
                }.map {
                    it[ForespoerselTable.vedtaksperiodeId] to tilForespoerselDto(it)
                }.toAggregateMap()
                .mapNotNull { (_, forespoersler) ->
                    val aktivForespoersel =
                        forespoersler
                            .sortedByDescending { it.opprettet }
                            .firstOrNull { it.status == Status.AKTIV }

                    val eksponertForespoerselId = forespoersler.finnEksponertForespoersel()?.forespoerselId

                    if (aktivForespoersel != null && eksponertForespoerselId != null) {
                        aktivForespoersel.copy(
                            forespoerselId = eksponertForespoerselId,
                        )
                    } else {
                        null
                    }
                }
        }

    private fun hentForespoerslerForVedtaksperiodeIdListe(
        vedtaksperiodeIdListe: List<UUID>,
    ): List<ForespoerselDto> =
        transaction(db) {
            ForespoerselTable
                .join(
                    BesvarelseTable,
                    JoinType.LEFT,
                    ForespoerselTable.id,
                    BesvarelseTable.fkForespoerselId,
                ).selectAll()
                .where { ForespoerselTable.vedtaksperiodeId inList vedtaksperiodeIdListe }
                .map(::tilForespoerselDto)
                .sortedBy { it.opprettet }
        }

    private fun hentVedtaksperiodeId(forespoerselId: UUID): UUID? =
        transaction(db) {
            ForespoerselTable
                .selectAll()
                .where {
                    ForespoerselTable.forespoerselId eq forespoerselId
                }.map {
                    it[ForespoerselTable.vedtaksperiodeId]
                }.firstOrNull()
        }

    private fun oppdaterStatuser(
        vedtaksperiodeId: UUID,
        erstattStatuser: Set<Status>,
        nyStatus: Status,
        activeTransaction: Transaction? = null,
    ): List<Long> {
        val whereStatement: SqlExpressionBuilder.() -> Op<Boolean> = {
            (ForespoerselTable.vedtaksperiodeId eq vedtaksperiodeId) and
                (ForespoerselTable.status inList erstattStatuser.map { it.name })
        }

        return activeTransaction
            .orNew {
                // Exposed støtter ikke Postgres sin RETURNING: https://github.com/JetBrains/Exposed/issues/1271
                val updated =
                    ForespoerselTable
                        .selectAll()
                        .where(whereStatement)
                        .map {
                            it[ForespoerselTable.id]
                        }

                ForespoerselTable.update(whereStatement) {
                    it[status] = nyStatus.name
                }

                updated
            }.also {
                val msg = "Oppdaterte ${it.size} rader med ny status '$nyStatus'. ids=$it"
                logger.info(msg)
                sikkerLogger.info(msg)
            }
    }

    private fun insertOrUpdateBesvarelse(
        forespoerselId: Long,
        forespoerselBesvart: LocalDateTime,
        imId: UUID?,
        activeTransaction: Transaction? = null,
    ) {
        activeTransaction.orNew {
            BesvarelseTable.upsert(BesvarelseTable.fkForespoerselId) {
                it[fkForespoerselId] = forespoerselId
                it[besvart] = forespoerselBesvart
                it[inntektsmeldingId] = imId
            }
        }
    }

    private fun <T> Transaction?.orNew(statement: Transaction.() -> T): T =
        if (this != null) {
            this.run(statement)
        } else {
            transaction(db, statement)
        }
}

fun tilForespoerselDto(row: ResultRow): ForespoerselDto {
    val orgnr = row[ForespoerselTable.orgnr].let(::Orgnr)
    val bestemmendeFravaersdager =
        row[ForespoerselTable.bestemmendeFravaersdager]
            .ifEmpty {
                mapOf(
                    // Gamle forespørsler som ikke har 'bestemmendeFravaersdager' _kan_ ha 'skjaeringstidspunkt',
                    // men vi vet ikke hvilket orgnr det tilhører
                    Orgnr("000000000") to row[ForespoerselTable.skjaeringstidspunkt],
                ).mapValuesNotNull { it }
            }

    val skjaeringstidspunkt =
        bestemmendeFravaersdager.minus(orgnr).minOfOrNull { it.value }

    return ForespoerselDto(
        forespoerselId = row[ForespoerselTable.forespoerselId],
        type = row[ForespoerselTable.type].let(Type::valueOf),
        status = row[ForespoerselTable.status].let(Status::valueOf),
        orgnr = orgnr,
        fnr = row[ForespoerselTable.fnr],
        vedtaksperiodeId = row[ForespoerselTable.vedtaksperiodeId],
        egenmeldingsperioder = row[ForespoerselTable.egenmeldingsperioder],
        sykmeldingsperioder = row[ForespoerselTable.sykmeldingsperioder],
        skjaeringstidspunkt = skjaeringstidspunkt,
        bestemmendeFravaersdager = bestemmendeFravaersdager,
        forespurtData = row[ForespoerselTable.forespurtData],
        besvarelse = tilBesvarelseMetadataDto(row),
        opprettet = row[ForespoerselTable.opprettet],
        oppdatert = row[ForespoerselTable.oppdatert],
    )
}

private fun tilBesvarelseMetadataDto(row: ResultRow): BesvarelseMetadataDto? =
    if (row.getOrNull(BesvarelseTable.id) != null) {
        BesvarelseMetadataDto(
            forespoerselBesvart = row[BesvarelseTable.besvart],
            inntektsmeldingId = row[BesvarelseTable.inntektsmeldingId],
        )
    } else {
        null
    }

private fun List<ForespoerselDto>.finnEksponertForespoersel(): ForespoerselDto? =
    sortedByDescending { it.opprettet }
        .zipWithNextOrNull()
        .firstOrNull { (_, next) ->
            next == null || next.status.erBesvart()
        }?.let { (current, _) -> current }

private fun List<Pair<UUID, ForespoerselDto>>.toAggregateMap(): Map<UUID, List<ForespoerselDto>> =
    fold(emptyMap()) { map, (vedtaksperiodeId, forespoersel) ->
        val forespoerslerForKey = map[vedtaksperiodeId].orEmpty().plus(forespoersel)

        map.plus(
            vedtaksperiodeId to forespoerslerForKey,
        )
    }
