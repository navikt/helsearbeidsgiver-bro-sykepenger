package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.Status
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

        return sessionOf(dataSource, returnGeneratedKey = true).use {
            it.run(queryOf(query, felter + jsonFelter).asUpdateAndReturnGeneratedKey)
        }
    }

    private fun forkastAlleAktiveForespoerslerFor(vedtaksperiodeId: UUID) = sessionOf(dataSource).use { session ->
        requireNotNull( // TODO: dis make sense???
            session.run(
                queryOf(
                    "UPDATE forespoersel SET status=:nyStatus WHERE vedtaksperiode_id=:vedtaksperiodeId AND status=:gammelStatus",
                    mapOf("vedtaksperiodeId" to vedtaksperiodeId, "nyStatus" to Status.FORKASTET.name, "gammelStatus" to Status.AKTIV.name)
                )
                    .asExecute
            )
        )
    }

    fun hentAktivForespørselFor(vedtaksperiodeId: UUID): ForespoerselDto? {
        val query = "SELECT * FROM forespoersel WHERE vedtaksperiode_id=:vedtaksperiode_id AND status='AKTIV' "
        val aktiveForespoersler = sessionOf((dataSource)).use {
            it.run(
                queryOf(query, mapOf("vedtaksperiode_id" to vedtaksperiodeId)).map { row ->
                    ForespoerselDto(
                        orgnr = row.string("orgnr"),
                        fnr = row.string("fnr"),
                        vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        forespurtData = Json.decodeFromString(row.string("forespurt_data")),
                        forespoerselBesvart = row.localDateTimeOrNull("forespoersel_besvart"),
                        status = Status.valueOf(row.string("status")),
                        opprettet = row.localDateTime("opprettet"),
                        oppdatert = row.localDateTime("oppdatert")

                    )
                }.asList
            )
        }

        if (aktiveForespoersler.size > 1) logger.error("Fant flere aktive forespørsler på vedtaksperiode: $vedtaksperiodeId")

        return aktiveForespoersler.maxByOrNull { it.opprettet }
    }

    fun hent(forespoerselId: Long): ForespoerselDto? {
        val query = "SELECT * FROM forespoersel WHERE id=:id"
        return sessionOf((dataSource)).use {
            it.run(
                queryOf(query, mapOf("id" to forespoerselId)).map { row ->
                    ForespoerselDto(
                        orgnr = row.string("orgnr"),
                        fnr = row.string("fnr"),
                        vedtaksperiodeId = row.uuid("vedtaksperiode_id"),
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        forespurtData = Json.decodeFromString(row.string("forespurt_data")),
                        forespoerselBesvart = row.localDateTimeOrNull("forespoersel_besvart"),
                        status = Status.valueOf(row.string("status")),
                        opprettet = row.localDateTime("opprettet"),
                        oppdatert = row.localDateTime("oppdatert")

                    )
                }.asSingle
            )
        }
    }
}
