package no.nav.helsearbeidsgiver.bro.sykepenger.db

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.zipWithNextOrNull
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.updateReturning
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
                .selectAll()
                .where {
                    ForespoerselTable.forespoerselId eq forespoerselId
                }.map(::tilForespoerselDto)
                .firstOrNull()
        }

    fun hentNyesteForespoerselForForespoerselId(
        forespoerselId: UUID,
        statuser: Set<Status>,
    ): ForespoerselDto? {
        val vedtaksperiodeId = hentVedtaksperiodeId(forespoerselId)

        return if (vedtaksperiodeId == null) {
            null
        } else {
            hentForespoerslerForVedtaksperiodeIdListe(setOf(vedtaksperiodeId))
                .finnNyesteForespoersel(statuser)
        }
    }

    fun hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId: UUID): ForespoerselDto? {
        val forespoersler = hentForespoerslerForVedtaksperiodeIdListe(setOf(vedtaksperiodeId))

        if (forespoersler.filter { it.status == Status.AKTIV }.size > 1) {
            "Fant flere aktive forespørsler for vedtaksperiode: $vedtaksperiodeId".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        return forespoersler.finnNyesteForespoersel(setOf(Status.AKTIV))
    }

    // TODO liste -> set
    fun hentForespoerslerEksponertTilSimba(vedtaksperiodeIdListe: List<UUID>): List<ForespoerselDto> =
        hentForespoerslerForVedtaksperiodeIdListe(vedtaksperiodeIdListe.toSet())
            .groupBy { it.vedtaksperiodeId }
            .mapNotNull { (_, forespoersler) ->
                // TODO bytt til 'finnNyesteForespoersel'
                forespoersler.finnEksponertForespoersel()
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
                }
        }.toAggregateMap()
            .mapNotNull { (_, forespoersler) ->
                forespoersler.finnNyesteForespoersel(setOf(Status.AKTIV))
            }

    fun hentForespoerslerForVedtaksperiodeIdListe(vedtaksperiodeIdListe: Set<UUID>): List<ForespoerselDto> =
        transaction(db) {
            ForespoerselTable
                .selectAll()
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
    ): List<Long> =
        activeTransaction
            .orNew {
                ForespoerselTable
                    .updateReturning(
                        returning = listOf(ForespoerselTable.id),
                        where = {
                            (ForespoerselTable.vedtaksperiodeId eq vedtaksperiodeId) and
                                (ForespoerselTable.status inList erstattStatuser.map { it.name })
                        },
                    ) {
                        it[status] = nyStatus.name
                    }.map {
                        it[ForespoerselTable.id]
                    }
            }.also {
                val msg = "Oppdaterte ${it.size} rader med ny status '$nyStatus'. ids=$it"
                logger.info(msg)
                sikkerLogger.info(msg)
            }

    fun markerKastetTilInfotrygd(vedtaksperiodeId: UUID): List<Long> =
        transaction(db) {
            ForespoerselTable
                .updateReturning(
                    returning = listOf(ForespoerselTable.id),
                    where = {
                        ForespoerselTable.vedtaksperiodeId eq vedtaksperiodeId
                    },
                ) {
                    it[kastetTilInfotrygd] = LocalDateTime.now().truncMillis()
                }.map {
                    it[ForespoerselTable.id]
                }.also {
                    val msg = "Oppdaterte ${it.size} rader med kastet til Infotrygd tidspunkt. ids=$it"
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

fun tilForespoerselDto(row: ResultRow): ForespoerselDto =
    ForespoerselDto(
        forespoerselId = row[ForespoerselTable.forespoerselId],
        type = row[ForespoerselTable.type].let(Type::valueOf),
        status = row[ForespoerselTable.status].let(Status::valueOf),
        orgnr = row[ForespoerselTable.orgnr].let(::Orgnr),
        fnr = row[ForespoerselTable.fnr],
        vedtaksperiodeId = row[ForespoerselTable.vedtaksperiodeId],
        egenmeldingsperioder = row[ForespoerselTable.egenmeldingsperioder],
        sykmeldingsperioder = row[ForespoerselTable.sykmeldingsperioder],
        bestemmendeFravaersdager = row[ForespoerselTable.bestemmendeFravaersdager],
        forespurtData = row[ForespoerselTable.forespurtData],
        opprettet = row[ForespoerselTable.opprettet],
        oppdatert = row[ForespoerselTable.oppdatert],
        kastetTilInfotrygd = row[ForespoerselTable.kastetTilInfotrygd],
    )

private fun List<ForespoerselDto>.finnNyesteForespoersel(statuser: Set<Status>): ForespoerselDto? {
    val nyesteForespoersel =
        sortedByDescending { it.opprettet }
            .firstOrNull { it.status in statuser }

    val eksponertForespoerselId = finnEksponertForespoersel()?.forespoerselId

    return if (nyesteForespoersel != null && eksponertForespoerselId != null) {
        // Simba kjenner kun til eksponerte forespørsel-ID-er, så vi må bytte for ID-en matcher Simbas systemer
        nyesteForespoersel.copy(
            forespoerselId = eksponertForespoerselId,
        )
    } else {
        null
    }
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
