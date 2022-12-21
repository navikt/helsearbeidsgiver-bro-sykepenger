package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespurtDataListe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.removeJsonWhitespace

class ForespurtDataDtoTest : FunSpec({
    val forespurtDataJson = "json/forespurtDataListe.json".readResource().removeJsonWhitespace()

    test("Forespurt data serialiseres korrekt") {
        val forespurtDataListe = mockForespurtDataListe()

        val serialisertJson = Json.encodeToString(forespurtDataListe)

        serialisertJson shouldBeEqualComparingTo forespurtDataJson
    }

    test("Forespurt data deserialiseres korrekt") {
        val forespurtDataListe = mockForespurtDataListe()

        val deserialisertJson = Json.decodeFromString<List<ForespurtDataDto>>(forespurtDataJson)

        deserialisertJson shouldContainExactly forespurtDataListe
    }
})

private fun String.readResource(): String =
    ClassLoader.getSystemClassLoader()
        .getResource(this)
        ?.readText()!!
