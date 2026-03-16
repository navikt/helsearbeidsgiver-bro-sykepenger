package no.nav.helsearbeidsgiver.bro.sykepenger.db

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.LocalDateTime
import java.util.UUID

class ForespoerselDao(
    private val db: Database,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun lagre(
        forespoersel: ForespoerselDto,
        eksponertForespoerselId: UUID,
    ): Long =
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
                    it[this.eksponertForespoerselId] = eksponertForespoerselId
                    it[vedtaksperiodeId] = forespoersel.vedtaksperiodeId
                    it[type] = forespoersel.type.name
                    it[status] = forespoersel.status.name
                    it[orgnr] = forespoersel.orgnr.verdi
                    it[fnr] = forespoersel.fnr.verdi
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

    fun oppdaterForespoerslerSomForkastet(vedtaksperiodeId: UUID): List<Long> =
        oppdaterStatuser(
            vedtaksperiodeId = vedtaksperiodeId,
            erstattStatuser = setOf(Status.AKTIV),
            nyStatus = Status.FORKASTET,
        )

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

    /*
    Behold denne metoden, kan være nyttig fra HAG-admin
     */
    fun hentForespoerslerForPerson(fnr: Fnr): List<ForespoerselDto> =
        transaction(db) {
            ForespoerselTable
                .selectAll()
                .where { ForespoerselTable.fnr eq fnr.toString() }
                .orderBy(ForespoerselTable.opprettet, SortOrder.DESC)
                .map {
                    tilForespoerselDto(it)
                }
        }

    fun hentVedtaksperiodeId(forespoerselId: UUID): UUID? =
        transaction(db) {
            ForespoerselTable
                .selectAll()
                .where {
                    ForespoerselTable.forespoerselId eq forespoerselId
                }.map {
                    it[ForespoerselTable.vedtaksperiodeId]
                }.firstOrNull()
        }

    fun hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId: UUID): ForespoerselDto? =
        hentForespoerslerEksponertTilSimba(
            setOf(vedtaksperiodeId),
            setOf(Status.AKTIV),
        ).firstOrNull()

    fun hentForespoerslerEksponertTilSimba(vedtaksperiodeIder: Set<UUID>): List<ForespoerselDto> =
        hentForespoerslerEksponertTilSimba(
            vedtaksperiodeIder,
            setOf(Status.AKTIV, Status.BESVART_SIMBA, Status.BESVART_SPLEIS),
        )

    fun hentForespoerslerForVedtaksperiodeIdListe(vedtaksperiodeIder: Set<UUID>): List<Pair<UUID, ForespoerselDto>> =
        transaction(db) {
            ForespoerselTable
                .selectAll()
                .where { ForespoerselTable.vedtaksperiodeId inList vedtaksperiodeIder }
                .map {
                    it[ForespoerselTable.eksponertForespoerselId] to tilForespoerselDto(it)
                }.sortedBy { it.second.opprettet }
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

    private fun hentForespoerslerEksponertTilSimba(
        vedtaksperiodeIder: Set<UUID>,
        statuser: Set<Status>,
    ): List<ForespoerselDto> =
        hentForespoerslerForVedtaksperiodeIdListe(vedtaksperiodeIder)
            .finnNyesteForespoerselPerVedtaksperiodeId(statuser)

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
            transaction(
                db = db,
                statement = statement,
            )
        }
}

fun tilForespoerselDto(row: ResultRow): ForespoerselDto =
    ForespoerselDto(
        forespoerselId = row[ForespoerselTable.forespoerselId],
        type = row[ForespoerselTable.type].let(Type::valueOf),
        status = row[ForespoerselTable.status].let(Status::valueOf),
        orgnr = row[ForespoerselTable.orgnr].let(::Orgnr),
        fnr = row[ForespoerselTable.fnr].let(::Fnr),
        vedtaksperiodeId = row[ForespoerselTable.vedtaksperiodeId],
        egenmeldingsperioder = row[ForespoerselTable.egenmeldingsperioder],
        sykmeldingsperioder = row[ForespoerselTable.sykmeldingsperioder],
        bestemmendeFravaersdager = row[ForespoerselTable.bestemmendeFravaersdager],
        forespurtData = row[ForespoerselTable.forespurtData],
        opprettet = row[ForespoerselTable.opprettet],
        oppdatert = row[ForespoerselTable.oppdatert],
        kastetTilInfotrygd = row[ForespoerselTable.kastetTilInfotrygd],
    )

private fun List<Pair<UUID, ForespoerselDto>>.finnNyesteForespoerselPerVedtaksperiodeId(statuser: Set<Status>): List<ForespoerselDto> =
    groupBy { it.second.vedtaksperiodeId }
        .mapNotNull { (_, eksponertIdOgForespoerselListe) ->
            eksponertIdOgForespoerselListe
                .sortedByDescending { it.second.opprettet }
                .firstOrNull { it.second.status in statuser }
                ?.let { (eksponertForespoerselId, nyesteForespoersel) ->
                    // Simba kjenner kun til eksponerte forespørsel-ID-er, så vi må bytte for at ID-en skal matche Simbas systemer.
                    nyesteForespoersel.copy(
                        forespoerselId = eksponertForespoerselId,
                    )
                }
        }.sortedBy { it.opprettet }
