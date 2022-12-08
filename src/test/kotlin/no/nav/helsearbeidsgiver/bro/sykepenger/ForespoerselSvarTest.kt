package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.removeJsonWhitespace

class ForespoerselSvarTest : FunSpec({
    test("data serialiseres korrekt") {
        val forespoerselSvar = mockForespoerselSvar()

        val expectedJson = """
            {
                "orgnr": "${forespoerselSvar.orgnr}",
                "fnr": "${forespoerselSvar.fnr}",
                "vedtaksperiodeId": "${forespoerselSvar.vedtaksperiodeId}",
                "fom": "${forespoerselSvar.fom}",
                "tom": "${forespoerselSvar.tom}",
                "forespurtData": ${forespoerselSvar.forespurtData.let(Json::encodeToString)},
                "eventType": "${forespoerselSvar.eventType}"
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselSvar.toJson().toString()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
