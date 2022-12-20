package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.removeJsonWhitespace

class ForespoerselMottattTest : FunSpec({
    test("data serialiseres korrekt") {
        val forespoerselMottatt = mockForespoerselMottatt()

        val expectedJson = """
            {
                "orgnr": "${forespoerselMottatt.orgnr}",
                "fnr": "${forespoerselMottatt.fnr}",
                "vedtaksperiodeId": "${forespoerselMottatt.vedtaksperiodeId}",
                "notis": "${forespoerselMottatt.notis}"
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselMottatt.toJson().toString()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
