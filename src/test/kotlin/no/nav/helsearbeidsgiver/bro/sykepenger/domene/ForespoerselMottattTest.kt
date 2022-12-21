package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.removeJsonWhitespace

class ForespoerselMottattTest : FunSpec({
    test("data serialiseres korrekt") {
        val forespoerselMottatt = mockForespoerselMottatt()

        val expectedJson = """
            {
                "${Pri.Key.NOTIS}": "${forespoerselMottatt.notis}",
                "${Pri.Key.ORGNR}": "${forespoerselMottatt.orgnr}",
                "${Pri.Key.FNR}": "${forespoerselMottatt.fnr}",
                "${Pri.Key.VEDTAKSPERIODE_ID}": "${forespoerselMottatt.vedtaksperiodeId}"
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselMottatt.toJson().toString()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
