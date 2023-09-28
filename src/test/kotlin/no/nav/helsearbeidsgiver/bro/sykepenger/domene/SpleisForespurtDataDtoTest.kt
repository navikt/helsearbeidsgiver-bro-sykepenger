package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespurtDataMedFastsattInntektListe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockSpleisForespurtDataListe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockSpleisForespurtDataMedForrigeInntektListe
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace

class SpleisForespurtDataDtoTest : FunSpec({
    listOf(
        row("forespurtDataListe", ::mockSpleisForespurtDataListe),
        row("forespurtDataMedFastsattInntektListe", ::mockForespurtDataMedFastsattInntektListe),
        row("forespurtDataMedForrigeInntektListe", ::mockSpleisForespurtDataMedForrigeInntektListe)
    )
        .forEach { (fileName, mockDataFn) ->
            val expectedJson = "json/$fileName.json".readResource().removeJsonWhitespace()

            test("Forespurt data serialiseres korrekt") {
                val forespurtDataListe = mockDataFn()

                val serialisertJson = forespurtDataListe.toJsonStr(SpleisForespurtDataDto.serializer().list())

                withClue("Validerer mot '$fileName'") {
                    serialisertJson shouldBe expectedJson
                }
            }

            test("Forespurt data deserialiseres korrekt") {
                val forespurtDataListe = mockDataFn()

                val deserialisertJson = expectedJson.parseJson().fromJson(SpleisForespurtDataDto.serializer().list())

                withClue("Validerer mot '$fileName'") {
                    deserialisertJson shouldContainExactly forespurtDataListe
                }
            }
        }
})

private fun String.readResource(): String =
    ClassLoader.getSystemClassLoader()
        .getResource(this)
        ?.readText()!!
