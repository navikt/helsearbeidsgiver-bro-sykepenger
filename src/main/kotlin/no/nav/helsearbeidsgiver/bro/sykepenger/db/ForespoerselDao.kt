package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespoerselDto
import javax.sql.DataSource

internal class ForespoerselDao(private val dataSource: DataSource) {
    fun lagre(forespoersel: ForespoerselDto) {
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
            "forespurt_data" to forespoersel.forespurtData.toJson()

        )
        val kolonnenavn = (felter + jsonFelter).keys.joinToString()
        val noekler = felter.keys.joinToString { ":$it" } + ", " + jsonFelter.keys.joinToString { ":$it::json" }
        val query = "INSERT INTO forespoersel($kolonnenavn) VALUES ($noekler)"
        sessionOf(dataSource).use {
            it.run(queryOf(query, felter + jsonFelter).asExecute)
        }
    }
}
