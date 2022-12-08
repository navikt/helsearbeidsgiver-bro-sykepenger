package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotliquery.Row
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.execute
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.hent
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.hentListe
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.updateAndReturnGeneratedKey
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

class ForespoerselDao(private val dataSource: DataSource) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun lagre(forespoersel: ForespoerselDto): Long? {
        forkastAlleAktiveForespoerslerFor(forespoersel.vedtaksperiodeId)

        val felter = mapOf(
            "fnr" to forespoersel.fnr,
            "orgnr" to forespoersel.orgnr,
            "vedtaksperiode_id" to forespoersel.vedtaksperiodeId,
            "fom" to forespoersel.fom,
            "tom" to forespoersel.tom,
            "forespoersel_besvart" to null,
            "status" to forespoersel.status.name,
            "opprettet" to forespoersel.opprettet,
            "oppdatert" to forespoersel.oppdatert
        )

        val jsonFelter = mapOf(
            "forespurt_data" to forespoersel.forespurtData.let(Json::encodeToString)
        )

        val kolonnenavn = (felter + jsonFelter).keys.joinToString()
        val noekler = listOf(
            felter.keys.joinToString { ":$it" },
            jsonFelter.keys.joinToString { ":$it::json" }
        ).joinToString()

        return updateAndReturnGeneratedKey(
            query = "INSERT INTO forespoersel($kolonnenavn) VALUES ($noekler)",
            params = felter + jsonFelter,
            dataSource = dataSource
        )
    }

    private fun forkastAlleAktiveForespoerslerFor(vedtaksperiodeId: UUID): Boolean =
        execute(
            query = "UPDATE forespoersel SET status=:nyStatus WHERE vedtaksperiode_id=:vedtaksperiodeId AND status=:gammelStatus",
            params = mapOf(
                "vedtaksperiodeId" to vedtaksperiodeId,
                "nyStatus" to Status.FORKASTET.name,
                "gammelStatus" to Status.AKTIV.name
            ),
            dataSource = dataSource
        )

    fun hentAktivForespoerselFor(vedtaksperiodeId: UUID): ForespoerselDto? {
        val aktiveForespoersler = hentListe(
            query = "SELECT * FROM forespoersel WHERE vedtaksperiode_id=:vedtaksperiode_id AND status='AKTIV'",
            params = mapOf("vedtaksperiode_id" to vedtaksperiodeId),
            dataSource = dataSource,
            transform = Row::toForespoerselDto
        )

        if (aktiveForespoersler.size > 1) logger.error("Fant flere aktive forespørsler på vedtaksperiode: $vedtaksperiodeId")

        return aktiveForespoersler.maxByOrNull { it.opprettet }
    }

    fun hentForespoersel(forespoerselId: Long): ForespoerselDto? =
        hent(
            query = "SELECT * FROM forespoersel WHERE id=:id",
            params = mapOf("id" to forespoerselId),
            dataSource = dataSource,
            transform = Row::toForespoerselDto
        )
}

private fun Row.toForespoerselDto(): ForespoerselDto =
    ForespoerselDto(
        orgnr = "orgnr".let(::string),
        fnr = "fnr".let(::string),
        vedtaksperiodeId = "vedtaksperiode_id".let(::uuid),
        fom = "fom".let(::localDate),
        tom = "tom".let(::localDate),
        forespurtData = "forespurt_data".let(::string).let(Json::decodeFromString),
        forespoerselBesvart = "forespoersel_besvart".let(::localDateTimeOrNull),
        status = "status".let(::string).let(Status::valueOf),
        opprettet = "opprettet".let(::localDateTime),
        oppdatert = "oppdatert".let(::localDateTime)
    )
