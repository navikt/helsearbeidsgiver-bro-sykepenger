package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.truncMillis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespurtDataListe
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
        val status = Status.AKTIV
        val timestamp = LocalDateTime.now().truncMillis()
    }

    private val forespoerselDao = ForespoerselDao(dataSource)

    @Test
    fun `Lagre forespørsel i databasen`() {
        val forespoersel = forespoerselDto()

        val forespoerselId = forespoerselDao.lagre(forespoersel)
        val lagretForespoersel = forespoerselDao.hent(forespoerselId!!)
        assertEquals(1, antallForespoersler())
        assertEquals(forespoersel, lagretForespoersel)
    }

    @Test
    fun `Forkaster alle aktive forespørsler knyttet til en vedtaksperiodeId når ny forespørsel med lik vedtaksperiodeId mottas`() {
        val (forespoerselId1, forespoerselId2) = List(2) {
            forespoerselDao.lagre(forespoerselDto())!!
        }
        oppdaterStatus(forespoerselId1)

        val forespoerselId3 = forespoerselDao.lagre(forespoerselDto())!!
        val forespoerselId4 = forespoerselDao.lagre(forespoerselDto().copy(vedtaksperiodeId = UUID.randomUUID()))!!

        val (
            forespoersel1,
            forespoersel2,
            forespoersel3,
            forespoersel4
        ) = listOf(forespoerselId1, forespoerselId2, forespoerselId3, forespoerselId4).map(forespoerselDao::hent)

        assertEquals(Status.FORKASTET, forespoersel1!!.status)
        assertEquals(Status.FORKASTET, forespoersel2!!.status)
        assertEquals(Status.AKTIV, forespoersel3!!.status)
        assertEquals(Status.AKTIV, forespoersel4!!.status)
    }

    private fun antallForespoersler() = sessionOf(dataSource).use { session ->
        requireNotNull(
            session.run(
                queryOf("SELECT COUNT(1) FROM forespoersel")
                    .map { it.int(1) }
                    .asSingle
            )
        )
    }

    private fun oppdaterStatus(forespoerselId: Long) = sessionOf(dataSource).use { session ->
        requireNotNull(
            session.run(
                queryOf("UPDATE forespoersel SET status = 'AKTIV' WHERE id=:id", mapOf("id" to forespoerselId))
                    .asExecute
            )
        )
    }

    private fun forespoerselDto() = ForespoerselDto(
        orgnr = ORGNR,
        fnr = FNR,
        vedtaksperiodeId = vedtaksveriodeId,
        fom = fom,
        tom = tom,
        forespurtData = mockForespurtDataListe(),
        forespoerselBesvart = null,
        status = status,
        opprettet = timestamp,
        oppdatert = timestamp
    )
}
