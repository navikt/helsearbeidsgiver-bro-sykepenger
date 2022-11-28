package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo

class ForespoerselMottattTest : FunSpec({
    test("data serialiseres korrekt") {
        val forespoerselMottatt = ForespoerselMottatt(
            orgnr = "123",
            fnr = "abc"
        )

        val expectedJson = """
            {
                "orgnr": "${forespoerselMottatt.orgnr}",
                "fnr": "${forespoerselMottatt.fnr}",
                "eventType": "${forespoerselMottatt.eventType}"
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselMottatt.toJson()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
