package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.BehovDTO
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespoerselDTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class ForespoerselDAOTest : AbstractDatabaseTest() {

    private companion object {
        const val FNR = "123456789"
        const val ORGNR = "4321"
        val VEDTAKSVERIODE_ID = UUID.randomUUID()
        val VEDTAKSVERIODE_FOM = LocalDate.MIN
        val VEDTAKSVERIODE_TOM = LocalDate.MAX
        val behov = BehovDTO()
        val status = null
        val timestamp = LocalDateTime.now()
    }

    @Test
    fun `Lagre forespørsel i databasen`() {
        val forespoerselDAO = ForespoerselDAO(dataSource)
        val forespoersel = ForespoerselDTO(
            organisasjonsnummer = ORGNR,
            fødselsnummer = FNR,
            vedtaksperiodeId = VEDTAKSVERIODE_ID,
            vedtaksperiodeFom = VEDTAKSVERIODE_FOM,
            vedtaksperiodeTom = VEDTAKSVERIODE_TOM,
            behov = behov,
            status = status,
            forespoerselBesvart = null,
            opprettet = timestamp,
            oppdatert = timestamp
        )
        forespoerselDAO.lagre(forespoersel)
        assertEquals(1, antallForespoersler())

        forespoerselDAO.lagre(forespoersel)
        assertEquals(2, antallForespoersler())
    }

    private fun antallForespoersler() = sessionOf((dataSource)).use { session ->
        requireNotNull(session.run(queryOf("SELECT COUNT(1) FROM forespoersel").map { it.int(1) }.asSingle))
    }
}
