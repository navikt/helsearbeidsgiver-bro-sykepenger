package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.removeJsonWhitespace
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson
import java.util.UUID

class ForespoerselSvarTest : FunSpec({
    test("ForespoerselSvar med suksess serialiseres korrekt") {
        val forespoerselSvarSuksess = mockForespoerselSvarSuksess()
        val forespoerselSvar = ForespoerselSvar(
            forespoerselId = UUID.randomUUID(),
            resultat = forespoerselSvarSuksess,
            boomerang = mapOf(
                "boom" to "shakalaka".toJson()
            )
        )

        val expectedJson = """
            { 
                "forespoerselId": "${forespoerselSvar.forespoerselId}",
                "resultat": {
                    "${Pri.Key.LØSNING}": "${forespoerselSvarSuksess.løsning}",
                    "${Pri.Key.ORGNR}": "${forespoerselSvarSuksess.orgnr}",
                    "${Pri.Key.FNR}": "${forespoerselSvarSuksess.fnr}",
                    "${Pri.Key.SYKMELDINGSPERIODER}": ${forespoerselSvarSuksess.sykmeldingsperioder.let(Json::encodeToString)},
                    "${Pri.Key.FORESPURT_DATA}": ${forespoerselSvarSuksess.forespurtData.let(Json::encodeToString)}
                }, 
                "boomerang": {
                    "boom": "shakalaka"
                }
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselSvar.toJson().toString()

        actualJson shouldBe expectedJson
    }

    test("ForespoerselSvar med feil serialiseres korrekt") {
        val forespoerselSvar = ForespoerselSvar(
            forespoerselId = UUID.randomUUID(),
            feil = ForespoerselSvarFeil.FORESPOERSEL_IKKE_FUNNET,
            boomerang = mapOf(
                "boom" to "shakalaka".toJson()
            )
        )

        val expectedJson = """
            { 
                "forespoerselId": "${forespoerselSvar.forespoerselId}",
                "feil": {
                    "feilkode": "${forespoerselSvar.feil?.name}",
                    "feilmelding": "${forespoerselSvar.feil?.feilmelding}"
                }, 
                "boomerang": {
                    "boom": "shakalaka"
                }
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselSvar.toJson().toString()

        actualJson shouldBe expectedJson
    }
})
