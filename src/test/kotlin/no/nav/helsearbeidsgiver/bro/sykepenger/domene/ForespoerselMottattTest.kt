package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.removeJsonWhitespace

class ForespoerselMottattTest : FunSpec({
    test("data serialiseres korrekt") {
        val forespoerselMottatt = mockForespoerselMottatt()

        val expectedJson = """
            {
                "${Pri.Key.FORESPOERSEL_ID}": "${forespoerselMottatt.forespoerselId}",
                "${Pri.Key.ORGNR}": "${forespoerselMottatt.orgnr}",
                "${Pri.Key.FNR}": "${forespoerselMottatt.fnr}"
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselMottatt.let(Json::encodeToString)

        actualJson shouldBe expectedJson
    }
})
