package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr

class ForespoerselSvarTest : FunSpec({
    withData(
        mapOf<String, ForespoerselSvar.() -> ForespoerselSvar>(
            "ForespoerselSvar med suksess serialiseres korrekt" to ForespoerselSvar::medSuksess,
            "ForespoerselSvar med feil serialiseres korrekt" to ForespoerselSvar::medFeil
        )
    ) { medSuksessEllerFeil ->
        val forespoerselSvar = mockForespoerselSvarUtenSuksessEllerFeil()
            .medSuksessEllerFeil()

        val expectedJson = forespoerselSvar.hardcodedJson()

        val actualJson = forespoerselSvar.toJsonStr(ForespoerselSvar.serializer())

        actualJson shouldBe expectedJson
    }

    withData(
        mapOf<String, ForespoerselSvar.() -> ForespoerselSvar>(
            "ForespoerselSvar med suksess deserialiseres korrekt" to ForespoerselSvar::medSuksess,
            "ForespoerselSvar med feil deserialiseres korrekt" to ForespoerselSvar::medFeil
        )
    ) { medSuksessEllerFeil ->
        val expectedInstance = mockForespoerselSvarUtenSuksessEllerFeil()
            .medSuksessEllerFeil()

        val expectedJson = expectedInstance.hardcodedJson()

        val actualInstance = shouldNotThrowAny {
            expectedJson.parseJson().fromJson(ForespoerselSvar.serializer())
        }

        actualInstance shouldBe expectedInstance
    }
})

private fun mockForespoerselSvarUtenSuksessEllerFeil(): ForespoerselSvar =
    ForespoerselSvar(
        forespoerselId = randomUuid(),
        boomerang = mapOf(
            "boom" to "shakalaka".toJson()
        )
            .toJson()
    )

private fun ForespoerselSvar.medSuksess(): ForespoerselSvar =
    copy(resultat = mockForespoerselSvarSuksess())

private fun ForespoerselSvar.medFeil(): ForespoerselSvar =
    copy(feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET)

private fun ForespoerselSvar.hardcodedJson(): String =
    """
    {
        "forespoerselId": "$forespoerselId",
        ${resultat.hardcodedJsonFieldOrEmpty()}
        ${feil.hardcodedJsonFieldOrEmpty()}
        "boomerang": {
            "boom": "shakalaka"
        }
    }
    """.removeJsonWhitespace()

private fun ForespoerselSvar.Suksess?.hardcodedJsonFieldOrEmpty(): String =
    this?.let {
        """
            "resultat": ${it.hardcodedJson()},
        """
    }
        ?.removeJsonWhitespace()
        .orEmpty()

private fun ForespoerselSvar.Feil?.hardcodedJsonFieldOrEmpty(): String =
    this?.let {
        """
            "feil": "$it",
        """
    }
        ?.removeJsonWhitespace()
        .orEmpty()

private fun ForespoerselSvar.Suksess.hardcodedJson(): String =
    """
    {
        "orgnr": "${orgnr.verdi}",
        "fnr": "$fnr",
        "sykmeldingsperioder": [${sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "egenmeldingsperioder": [${egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "forespurtData": ${forespurtData.hardcodedJson()}
    }
    """.removeJsonWhitespace()

private fun List<ForespurtDataDto>.hardcodedJson(): String =
    joinToString(prefix = "[", postfix = "]") {
        when (it) {
            is ArbeidsgiverPeriode ->
                """{ "opplysningstype": "Arbeidsgiverperiode" }"""
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
            is Refusjon ->
                """
                {
                    "opplysningstype": "Refusjon", 
                    "forslag": [${it.forslag.joinToString(transform = ForslagRefusjon::hardcodedJson)}]
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

private fun ForslagRefusjon.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "tom": ${tom.jsonStrOrNull()},
        "beløp": $beløp
    }
    """

private fun <T : Any> T?.jsonStrOrNull(): String? =
    this?.let { "\"it\"" }
