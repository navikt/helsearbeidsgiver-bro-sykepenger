package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.removeJsonWhitespace

class ForespoerselSvarTest : FunSpec({
    test("data serialiseres korrekt") {
        val forespoerselSvar = mockForespoerselSvar()

        val expectedJson = """
            {
                "${Pri.Key.LØSNING}": "${forespoerselSvar.løsning}",
                "${Pri.Key.ORGNR}": "${forespoerselSvar.orgnr}",
                "${Pri.Key.FNR}": "${forespoerselSvar.fnr}",
                "${Pri.Key.FORESPOERSEL_ID}": "${forespoerselSvar.forespoerselId}",
                "${Pri.Key.SYKMELDINGSPERIODER}": ${forespoerselSvar.sykmeldingsperioder.let(Json::encodeToString)},
                "${Pri.Key.FORESPURT_DATA}": ${forespoerselSvar.forespurtData.let(Json::encodeToString)},
                "${Pri.Key.BOOMERANG}": ${forespoerselSvar.boomerang.let(Json::encodeToString)}
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselSvar.toJson().toString()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
