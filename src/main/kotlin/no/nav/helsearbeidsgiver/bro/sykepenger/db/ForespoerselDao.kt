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
            "forespurt_data" to Json.encodeToString(forespoersel.forespurtData)
        )
        val kolonnenavn = (felter + jsonFelter).keys.joinToString()
        val noekler = felter.keys.joinToString { ":$it" } + ", " + jsonFelter.keys.joinToString { ":$it::json" }
        val query = "INSERT INTO forespoersel($kolonnenavn) VALUES ($noekler)"

        return updateAndReturnGeneratedKey(dataSource, query, felter + jsonFelter)
    }

    private fun forkastAlleAktiveForespoerslerFor(vedtaksperiodeId: UUID) = execute(
        dataSource = dataSource,
        query = "UPDATE forespoersel SET status=:nyStatus WHERE vedtaksperiode_id=:vedtaksperiodeId AND status=:gammelStatus",
        params = mapOf("vedtaksperiodeId" to vedtaksperiodeId, "nyStatus" to Status.FORKASTET.name, "gammelStatus" to Status.AKTIV.name)
    )

    fun hentAktivForespoerselFor(vedtaksperiodeId: UUID): ForespoerselDto? {
        val aktiveForespoersler = hentListe(
            dataSource = dataSource,
            query = "SELECT * FROM forespoersel WHERE vedtaksperiode_id=:vedtaksperiode_id AND status='AKTIV' ",
            params = mapOf("vedtaksperiode_id" to vedtaksperiodeId)
        ) { row -> row.toForespoerselDto() }

        if (aktiveForespoersler.size > 1) logger.error("Fant flere aktive forespørsler på vedtaksperiode: $vedtaksperiodeId")

        return aktiveForespoersler.maxByOrNull { it.opprettet }
    }

    fun hentForespoersel(forespoerselId: Long) = hent(
        dataSource = dataSource,
        query = "SELECT * FROM forespoersel WHERE id=:id",
        params = mapOf("id" to forespoerselId)
    ) { row -> row.toForespoerselDto() }

    private fun Row.toForespoerselDto() = ForespoerselDto(
        orgnr = string("orgnr"),
        fnr = string("fnr"),
        vedtaksperiodeId = uuid("vedtaksperiode_id"),
        fom = localDate("fom"),
        tom = localDate("tom"),
        forespurtData = Json.decodeFromString(string("forespurt_data")),
        forespoerselBesvart = localDateTimeOrNull("forespoersel_besvart"),
        status = Status.valueOf(string("status")),
        opprettet = localDateTime("opprettet"),
        oppdatert = localDateTime("oppdatert")

    )
}
