package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import java.util.UUID

class ForespoerselMottattTest : FunSpec({
    test("data serialiseres korrekt") {
        val forespoerselMottatt = ForespoerselMottatt(
            orgnr = "123",
            fnr = "abc",
            vedtaksperiodeId = UUID.randomUUID()
        )

        val expectedJson = """
            {
                "orgnr": "${forespoerselMottatt.orgnr}",
                "fnr": "${forespoerselMottatt.fnr}",
                "vedtaksperiodeId": "${forespoerselMottatt.vedtaksperiodeId}",
                "eventType": "${forespoerselMottatt.eventType}"
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselMottatt.toJson().toString()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
