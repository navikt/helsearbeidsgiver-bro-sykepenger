package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
                    "orgnr": "${forespoerselSvarSuksess.orgnr}",
                    "fnr": "${forespoerselSvarSuksess.fnr}",
                    "sykmeldingsperioder": [${forespoerselSvarSuksess.sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
                    "forespurtData": ${forespoerselSvarSuksess.forespurtData.hardcodedJson()}
                },
                "boomerang": {
                    "boom": "shakalaka"
                }
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselSvar.let(Json::encodeToString)

        actualJson shouldBe expectedJson
    }

    test("ForespoerselSvar med feil serialiseres korrekt") {
        val forespoerselSvar = ForespoerselSvar(
            forespoerselId = UUID.randomUUID(),
            feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET,
            boomerang = mapOf(
                "boom" to "shakalaka".toJson()
            )
        )

        val expectedJson = """
            {
                "forespoerselId": "${forespoerselSvar.forespoerselId}",
                "feil": "${forespoerselSvar.feil}",
                "boomerang": {
                    "boom": "shakalaka"
                }
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselSvar.let(Json::encodeToString)

        actualJson shouldBe expectedJson
    }
})

private fun List<ForespurtDataDto>.hardcodedJson(): String =
    joinToString(prefix = "[", postfix = "]") {
        when (it) {
            is ArbeidsgiverPeriode ->
                """
                {
                    "opplysningstype": "Arbeidsgiverperiode",
                    "forslag": [${it.forslag.joinToString(transform = Periode::hardcodedJson)}]
                }
                """
            is Inntekt ->
                """
                {
                    "opplysningstype": "Inntekt",
                    "forslag": {
                        "beregningsmåneder": [${it.forslag.beregningsmåneder.joinToString { yearMonth -> "\"$yearMonth\"" }}]
                    }
                }
                """
            is FastsattInntekt ->
                """
                {
                    "opplysningstype": "FastsattInntekt",
                    "fastsattInntekt": ${it.fastsattInntekt}
                }
                """
            Refusjon ->
                """
                {
                    "opplysningstype": "Refusjon"
                }
                """
        }
    }

private fun Periode.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "tom": "$tom"
    }
    """
