package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr

class OrgnrTest : FunSpec({
    test("data serialiseres korrekt") {
        val orgnr = mockOrgnr()

        val expectedJson = orgnr.hardcodedJson()

        val actualJson = orgnr.toJsonStr(Orgnr.serializer())

        actualJson shouldBe expectedJson
    }

    test("data deserialiseres korrekt") {
        val expectedInstance = mockOrgnr()

        val expectedJson = expectedInstance.hardcodedJson()

        val actualInstance = shouldNotThrowAny {
            expectedJson.parseJson().fromJson(Orgnr.serializer())
        }

        actualInstance shouldBe expectedInstance
    }
})

private fun mockOrgnr(): Orgnr =
    Orgnr("885927409")

private fun Orgnr.hardcodedJson(): String =
    "\"$verdi\""
