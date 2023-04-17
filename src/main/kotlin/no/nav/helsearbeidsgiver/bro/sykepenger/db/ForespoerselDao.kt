package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.Row
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.execute
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.fromJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.list
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.listResult
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.nullableResult
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.parseJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJsonStr
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.updateAndReturnGeneratedKey
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

class ForespoerselDao(private val dataSource: DataSource) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun lagre(forespoersel: ForespoerselDto): Long? {
        forkastAlleAktiveForespoerslerFor(forespoersel.vedtaksperiodeId)

        val felter = mapOf(
            "forespoersel_id" to forespoersel.forespoerselId,
            "fnr" to forespoersel.fnr,
            "orgnr" to forespoersel.orgnr.verdi,
            "vedtaksperiode_id" to forespoersel.vedtaksperiodeId,
            "skjaeringstidspunkt" to forespoersel.skjaeringstidspunkt,
            "forespoersel_besvart" to null,
            "status" to forespoersel.status.name,
            "opprettet" to forespoersel.opprettet,
            "oppdatert" to forespoersel.oppdatert
        )

        val jsonFelter = mapOf(
            "sykmeldingsperioder" to forespoersel.sykmeldingsperioder.toJsonStr(Periode.serializer().list()),
            "forespurt_data" to forespoersel.forespurtData.toJsonStr(ForespurtDataDto.serializer().list())
        )

        val kolonnenavn = (felter + jsonFelter).keys.joinToString()
        val noekler = listOf(
            felter.keys.joinToString { ":$it" },
            jsonFelter.keys.joinToString { ":$it::json" }
        ).joinToString()

        return "INSERT INTO forespoersel($kolonnenavn) VALUES ($noekler)"
            .updateAndReturnGeneratedKey(
                params = felter + jsonFelter,
                dataSource = dataSource
            )
    }

    private fun forkastAlleAktiveForespoerslerFor(vedtaksperiodeId: UUID): Boolean =
        "UPDATE forespoersel SET status=:nyStatus WHERE vedtaksperiode_id=:vedtaksperiodeId AND status=:gammelStatus"
            .execute(
                params = mapOf(
                    "vedtaksperiodeId" to vedtaksperiodeId,
                    "nyStatus" to Status.FORKASTET.name,
                    "gammelStatus" to Status.AKTIV.name
                ),
                dataSource = dataSource
            )

    fun hentAktivForespoerselFor(forespoerselId: UUID): ForespoerselDto? =
        hentVedtaksperiodeId(forespoerselId)
            ?.let { vedtaksperiodeId ->
                "SELECT * FROM forespoersel WHERE vedtaksperiode_id=:vedtaksperiode_id AND status='AKTIV'"
                    .listResult(
                        params = mapOf("vedtaksperiode_id" to vedtaksperiodeId),
                        dataSource = dataSource,
                        transform = Row::toForespoerselDto
                    )
                    .also {
                        if (it.size > 1) logger.error("Fant flere aktive forespørsler for vedtaksperiode: $vedtaksperiodeId")
                    }
            }
            ?.maxByOrNull { it.opprettet }

    private fun hentVedtaksperiodeId(forespoerselId: UUID): UUID? =
        "SELECT vedtaksperiode_id FROM forespoersel WHERE forespoersel_id=:forespoersel_id"
            .nullableResult(
                params = mapOf("forespoersel_id" to forespoerselId),
                dataSource = dataSource,
                transform = Row::toUUID
            )
}

private fun Row.toUUID(): UUID = "vedtaksperiode_id".let(::uuid)

fun Row.toForespoerselDto(): ForespoerselDto =
    ForespoerselDto(
        forespoerselId = "forespoersel_id".let(::uuid),
        orgnr = "orgnr".let(::string).let(::Orgnr),
        fnr = "fnr".let(::string),
        vedtaksperiodeId = "vedtaksperiode_id".let(::uuid),
        skjaeringstidspunkt = "skjaeringstidspunkt".let(::localDate),
        sykmeldingsperioder = "sykmeldingsperioder".let(::string).parseJson().fromJson(Periode.serializer().list()),
        forespurtData = "forespurt_data".let(::string).parseJson().fromJson(ForespurtDataDto.serializer().list()),
        forespoerselBesvart = "forespoersel_besvart".let(::localDateTimeOrNull),
        status = "status".let(::string).let(Status::valueOf),
        opprettet = "opprettet".let(::localDateTime),
        oppdatert = "oppdatert".let(::localDateTime)
    )
