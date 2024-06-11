package no.nav.helsearbeidsgiver.bro.sykepenger.db

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.BesvarelseMetadataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.zipWithNextOrNull
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDateTime
import java.util.UUID

class ForespoerselDao(override val db: Database) : StatusDao() {
    override val statusTable = ForespoerselTable

    override fun tilForespoerselDto(row: ResultRow): ForespoerselDto {
        val orgnr = row[ForespoerselTable.orgnr].let(::Orgnr)
        val bestemmendeFravaersdager =
            row[ForespoerselTable.bestemmendeFravaersdager]
                .ifEmpty {
                    mapOf(
                        // Gamle forespørsler som ikke har 'bestemmendeFravaersdager' _kan_ ha 'skjaeringstidspunkt',
                        // men vi vet ikke hvilket orgnr det tilhører
                        Orgnr("000000000") to row[ForespoerselTable.skjaeringstidspunkt],
                    )
                        .mapValuesNotNull { it }
                }

        val skjaeringstidspunkt =
            bestemmendeFravaersdager.minus(orgnr).minOfOrNull { it.value }

        return ForespoerselDto(
            forespoerselId = row[ForespoerselTable.forespoerselId],
            vedtaksperiodeId = row[ForespoerselTable.vedtaksperiodeId],
            type = row[ForespoerselTable.type].let(Type::valueOf),
            status = row[ForespoerselTable.status].let(Status::valueOf),
            orgnr = orgnr,
            fnr = row[ForespoerselTable.fnr],
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

    fun lagre(forespoersel: ForespoerselDto): Long =
        transaction(db) {
            oppdaterStatuser(
                vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                erstattStatuser = setOf(Status.AKTIV),
                nyStatus = Status.FORKASTET,
                activeTransaction = this,
            )

            ForespoerselTable.insert {
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
            }
                .let {
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
                upsertBesvarelse(id, besvart, inntektsmeldingId, this)
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
                upsertBesvarelse(id, besvart, null, this)
            }

            oppdaterteForespoersler.size
        }

    fun hentNyesteForespoerselMedBesvarelseForForespoerselId(
        forespoerselId: UUID,
        statuser: Set<Status>,
    ): ForespoerselDto? =
        transaction(db) {
            ForespoerselTable.join(
                BesvarelseTable,
                JoinType.LEFT,
                ForespoerselTable.id,
                BesvarelseTable.fkForespoerselId,
            )
                .selectAll()
                .where {
                    val vedtaksperiodeIdQuery = hentVedtaksperiodeIdQuery(forespoerselId)

                    (ForespoerselTable.vedtaksperiodeId eqSubQuery vedtaksperiodeIdQuery) and
                        (ForespoerselTable.status inList statuser.map { it.name })
                }
                .map(::tilForespoerselDto)
                .maxByOrNull { it.opprettet }
        }

    fun hentForespoerselIdEksponertTilSimba(vedtaksperiodeId: UUID): UUID? =
        hentForespoerslerForVedtaksperiodeId(vedtaksperiodeId, Status.entries.toSet())
            .sortedByDescending { it.opprettet }
            .zipWithNextOrNull()
            .firstOrNull { (_, next) ->
                next == null || next.status.erBesvart()
            }
            ?.let { (current, _) -> current }
            ?.forespoerselId

    private fun upsertBesvarelse(
        fkForespoerselId: Long,
        besvart: LocalDateTime,
        imId: UUID?,
        activeTransaction: Transaction,
    ) {
        activeTransaction.run {
            BesvarelseTable.upsert(BesvarelseTable.fkForespoerselId) {
                it[this.fkForespoerselId] = fkForespoerselId
                it[this.besvart] = besvart
                it[inntektsmeldingId] = imId
            }
        }
    }
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
