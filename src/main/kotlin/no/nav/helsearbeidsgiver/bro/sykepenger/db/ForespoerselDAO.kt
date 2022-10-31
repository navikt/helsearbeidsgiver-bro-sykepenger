package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespoerselDTO
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

internal class ForespoerselDAO(private val dataSource: DataSource) {
    fun lagre(forespoersel: ForespoerselDTO) {
        @Language("PostgreSQL")
        val query = "INSERT INTO forespoersel(fnr, organisasjonsnummer, vedtakperiodeId, vedtaksperiodeFom, vedtaksperiodeTom, behov, status,forespoerselBesvart, opprettet, oppdatert) VALUES(?)"
        val map = mapOf(
            "fnr" to forespoersel.fødselsnummer,
            "organisasjonsnummer" to forespoersel.organisasjonsnummer,
            "vedtakperiopdeId" to forespoersel.vedtaksperiodeId,
            "vedtakperiopdeFom" to forespoersel.vedtaksperiodeFom,
            "vedtakperiopdeTom" to forespoersel.vedtaksperiodeTom,
            "behov" to forespoersel.behov,
            "status" to forespoersel.status,
            "forespoerselBesvart" to null,
            "opprettet" to forespoersel.opprettet,
            "oppdatert" to forespoersel.oppdatert
        )
        sessionOf(dataSource).use {
            it.run(queryOf(query, map).asExecute)
        }
    }
}
