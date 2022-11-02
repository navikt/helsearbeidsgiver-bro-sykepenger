package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class ForespoerselDaoTest : AbstractDatabaseTest() {

    private companion object {
        const val FNR = "123456789"
        const val ORGNR = "4321"
        val vedtaksveriodeId = UUID.randomUUID()
        val fom = LocalDate.EPOCH
        val tom = LocalDate.EPOCH.plusMonths(1)
        val forespurtData = ForespurtDataDto()
        val status = Status.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER
        val timestamp = LocalDateTime.now()
    }

    @Test
    fun `Lagre forespørsel i databasen`() {
        val forespoerselDao = ForespoerselDao(dataSource)
        val forespoersel = ForespoerselDto(
            orgnr = ORGNR,
            fnr = FNR,
            vedtaksperiodeId = vedtaksveriodeId,
            fom = fom,
            tom = tom,
            forespurtData = forespurtData,
            forespoerselBesvart = null,
            status = status,
            opprettet = timestamp,
            oppdatert = timestamp
        )
        forespoerselDao.lagre(forespoersel)
        assertEquals(1, antallForespoersler())
    }

    private fun antallForespoersler() = sessionOf((dataSource)).use { session ->
        requireNotNull(session.run(queryOf("SELECT COUNT(1) FROM forespoersel").map { it.int(1) }.asSingle))
    }
}
