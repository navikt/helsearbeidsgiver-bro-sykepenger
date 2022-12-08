package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespurtDataListe
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.removeJsonWhitespace

class ForespurtDataDtoTest : StringSpec({
    val forespurtDataJson = "json/forespurtDataListe.json".readResource().removeJsonWhitespace()

    "Forespurt data serialiseres korrekt" {
        val forespurtDataListe = mockForespurtDataListe()

        val serialisertJson = Json.encodeToString(forespurtDataListe)

        serialisertJson shouldBeEqualComparingTo forespurtDataJson
    }

    "Forespurt data deserialiseres korrekt" {
        val forespurtDataListe = mockForespurtDataListe()

        val deserialisertJson = Json.decodeFromString<List<ForespurtDataDto>>(forespurtDataJson)

        deserialisertJson shouldContainExactly forespurtDataListe
    }
})

private fun String.readResource(): String =
    ClassLoader.getSystemClassLoader()
        .getResource(this)
        ?.readText()!!
