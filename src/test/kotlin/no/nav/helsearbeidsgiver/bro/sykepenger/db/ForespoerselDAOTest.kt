package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.queryOf
import kotliquery.sessionOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ForespoerselDAOTest : AbstractDatabaseTest() {

    @Test
    fun `Lagre forespørsel i databasen`() {
        val forespoerselDAO = ForespoerselDAO(dataSource)
        forespoerselDAO.lagre()
        assertEquals(1, antallForespoersler())
    }

    private fun antallForespoersler() = sessionOf((dataSource)).use { session ->
        requireNotNull(session.run(queryOf("SELECT COUNT(1) FROM forespoersel").map { it.int(1) }.asSingle))
    }
}
