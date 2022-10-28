package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

internal class ForespoerselDAO(private val dataSource: DataSource) {
    fun lagre(foedselsnummer: String) {
        @Language("PostgreSQL")
        val query = "INSERT INTO forespoersel(fnr) VALUES(?)"

        sessionOf(dataSource).use {
            it.run(queryOf(query, foedselsnummer.toLong()).asExecute)
        }
    }
}
