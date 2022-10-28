package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.queryOf
import kotliquery.sessionOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ForespoerselDAOTest : AbstractDatabaseTest() {

    private companion object {
        const val FNR = "123456789"
    }

    @Test
    fun `Lagre forespørsel i databasen`() {
        val forespoerselDAO = ForespoerselDAO(dataSource)
        forespoerselDAO.lagre(FNR)
        assertEquals(1, antallForespoersler())

        forespoerselDAO.lagre(FNR)
        assertEquals(2, antallForespoersler())
    }

    private fun antallForespoersler() = sessionOf((dataSource)).use { session ->
        requireNotNull(session.run(queryOf("SELECT COUNT(1) FROM forespoersel").map { it.int(1) }.asSingle))
    }
}
