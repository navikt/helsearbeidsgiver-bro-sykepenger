package no.nav.helsearbeidsgiver.bro.sykepenger.db

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespurtDataListe
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

private val jsonWhitespaceRegex = Regex("""("(?:\\"|[^"])*")|\s""")

class ForespurtDataDtoTest {
    val forespurtDataJson = "json/forespurtDataListe.json".readResource().removeJsonWhitespace()

    @Test
    fun `Forespurt data serialiseres korrekt`() {
        val forespurtDataListe = mockForespurtDataListe()

        val serialisertJson = Json.encodeToString(forespurtDataListe)

        Assertions.assertEquals(forespurtDataJson, serialisertJson)
    }

    @Test
    fun `Forespurt data deserialiseres korrekt`() {
        val forespurtDataListe = mockForespurtDataListe()

        val deserialisertJson = Json.decodeFromString<List<ForespurtDataDto>>(forespurtDataJson)

        Assertions.assertEquals(forespurtDataListe, deserialisertJson)
    }
}

private fun String.readResource(): String =
    ClassLoader.getSystemClassLoader()
        .getResource(this)
        ?.readText()!!

private fun String.removeJsonWhitespace(): String =
    replace(jsonWhitespaceRegex, "$1")
