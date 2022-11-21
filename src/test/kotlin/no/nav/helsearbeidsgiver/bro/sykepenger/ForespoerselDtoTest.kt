package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.januar
import org.junit.jupiter.api.Test

internal class ForespoerselDtoTest {
    @Test
    fun `serialiserer dto riktig`() {
        // TODO: Skriv om til Kotest
        val resultat = Json.encodeToString(mockForespurtDataListe())
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
