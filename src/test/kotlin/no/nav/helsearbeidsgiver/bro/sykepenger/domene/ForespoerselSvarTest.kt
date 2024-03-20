package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import java.time.LocalDate

class ForespoerselSvarTest : FunSpec({
    withData(
        mapOf<String, ForespoerselSvar.() -> ForespoerselSvar>(
            "ForespoerselSvar med suksess serialiseres korrekt" to ForespoerselSvar::medSuksess,
            "ForespoerselSvar med feil serialiseres korrekt" to ForespoerselSvar::medFeil,
        ),
    ) { medSuksessEllerFeil ->
        val forespoerselSvar =
            mockForespoerselSvarUtenSuksessEllerFeil()
                .medSuksessEllerFeil()

        val expectedJson = forespoerselSvar.hardcodedJson()

        val actualJson = forespoerselSvar.toJsonStr(ForespoerselSvar.serializer())

        actualJson shouldBe expectedJson
    }

    withData(
        mapOf<String, ForespoerselSvar.() -> ForespoerselSvar>(
            "ForespoerselSvar med suksess deserialiseres korrekt" to ForespoerselSvar::medSuksess,
            "ForespoerselSvar med feil deserialiseres korrekt" to ForespoerselSvar::medFeil,
        ),
    ) { medSuksessEllerFeil ->
        val expectedInstance =
            mockForespoerselSvarUtenSuksessEllerFeil()
                .medSuksessEllerFeil()

        val expectedJson = expectedInstance.hardcodedJson()

        val actualInstance =
            shouldNotThrowAny {
                expectedJson.parseJson().fromJson(ForespoerselSvar.serializer())
            }

        actualInstance shouldBe expectedInstance
    }
})

private fun mockForespoerselSvarUtenSuksessEllerFeil(): ForespoerselSvar =
    ForespoerselSvar(
        forespoerselId = randomUuid(),
        boomerang =
            mapOf(
                "boom" to "shakalaka".toJson(),
            )
                .toJson(),
    )

private fun ForespoerselSvar.medSuksess(): ForespoerselSvar = copy(resultat = mockForespoerselSvarSuksess())

private fun ForespoerselSvar.medFeil(): ForespoerselSvar = copy(feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET)

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
        "type": "$type",
        "orgnr": "${orgnr.verdi}",
        "fnr": "$fnr",
        "vedtaksperiodeId": "$vedtaksperiodeId",
        "egenmeldingsperioder": [${egenmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "sykmeldingsperioder": [${sykmeldingsperioder.joinToString(transform = Periode::hardcodedJson)}],
        "skjaeringstidspunkt": "$skjaeringstidspunkt",
        "bestemmendeFravaersdager": {${bestemmendeFravaersdager.toList().joinToString(transform = Pair<Orgnr, LocalDate>::hardcodedJson)}},
        "forespurtData": ${forespurtData.hardcodedJson()},
        "erBesvart": $erBesvart
    }
    """.removeJsonWhitespace()

private fun Pair<Orgnr, LocalDate>.hardcodedJson(): String =
    """
        "${first.verdi}": "$second"
    """

private fun ForespurtData.hardcodedJson(): String =
    """
    {
        "arbeidsgiverperiode": {
            "paakrevd": ${arbeidsgiverperiode.paakrevd}
        },
        "inntekt": {
            "paakrevd": ${inntekt.paakrevd},
            "forslag": ${inntekt.forslag.hardcodedJson()}
        },
        "refusjon": {
            "paakrevd": ${refusjon.paakrevd},
            "forslag": ${refusjon.forslag.hardcodedJson()}
        }
    }
    """

private fun ForslagInntekt.hardcodedJson(): String =
    when (this) {
        is ForslagInntekt.Grunnlag ->
            """
            {
                "type": "ForslagInntektGrunnlag",
                "forrigeInntekt": ${forrigeInntekt?.hardcodedJson()}
            }
            """

        is ForslagInntekt.Fastsatt ->
            """
            {
                "type": "ForslagInntektFastsatt",
                "fastsattInntekt": $fastsattInntekt
            }
            """
    }

private fun ForrigeInntekt.hardcodedJson(): String =
    """
    {
        "skjæringstidspunkt": "$skjæringstidspunkt",
        "kilde": "$kilde",
        "beløp": $beløp
    }
    """

private fun ForslagRefusjon.hardcodedJson(): String =
    """
    {
        "perioder": [${perioder.joinToString(transform = ForslagRefusjon.Periode::hardcodedJson)}],
        "opphoersdato": ${opphoersdato.jsonStrOrNull()}
    }
    """

private fun ForslagRefusjon.Periode.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "beloep": $beloep
    }
    """

private fun Periode.hardcodedJson(): String =
    """
    {
        "fom": "$fom",
        "tom": "$tom"
    }
    """

private fun <T : Any> T?.jsonStrOrNull(): String? = this?.let { "\"$it\"" }
