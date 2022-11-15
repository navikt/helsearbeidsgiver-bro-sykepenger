package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helsearbeidsgiver.bro.sykepenger.ArbeidsgiverPeriode
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.Forslag
import no.nav.helsearbeidsgiver.bro.sykepenger.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.truncMillis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.januar
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
        val status = Status.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER
        val timestamp = LocalDateTime.now().truncMillis()
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
            forespurtData = mockForespurtDataListe(),
            forespoerselBesvart = null,
            status = status,
            opprettet = timestamp,
            oppdatert = timestamp
        )

        val forespoerselId = forespoerselDao.lagre(forespoersel)
        val lagretForespoersel = forespoerselDao.hent(forespoerselId!!)
        assertEquals(1, antallForespoersler())
        assertEquals(forespoersel, lagretForespoersel)
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
}

private fun mockForespurtDataListe(): List<ForespurtDataDto> =
    listOf(
        ArbeidsgiverPeriode(
            forslag = listOf(
                Forslag(
                    fom = 1.januar,
                    tom = 10.januar
                ),
                Forslag(
                    fom = 15.januar,
                    tom = 20.januar
                )
            )
        ),
        Refusjon,
        Inntekt
    )
