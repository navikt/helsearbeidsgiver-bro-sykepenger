package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespurtDataSpleisRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockSpleisForespurtDataListe
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.resource.readResource

class SpleisForespurtDataDtoTest :
    FunSpec({
        listOf(
            "forespurtDataListe" to ::mockSpleisForespurtDataListe,
            "forespurtDataListeMedTomtInntektForslag" to ::mockSpleisForespurtDataListeMedTomtInntektForslag,
            "forespurtDataListeMedForrigeInntekt" to ::mockSpleisForespurtDataListeMedForrigeInntekt,
        ).forEach { (fileName, mockDataFn) ->
            val expectedJson = "json/$fileName.json".readResource().removeJsonWhitespace()

            test("Forespurt data serialiseres korrekt") {
                val forespurtDataListe = mockDataFn()

                val serialisertJson = forespurtDataListe.toJsonStr(SpleisForespurtDataDto.serializer().list())

                withClue("Validerer mot '$fileName'") {
                    serialisertJson shouldBe expectedJson
                }
            }

            test("Forespurt data deserialiseres korrekt") {
                val forespurtDataListe = mockDataFn()

                val deserialisertJson = expectedJson.parseJson().fromJson(SpleisForespurtDataDto.serializer().list())

                withClue("Validerer mot '$fileName'") {
                    deserialisertJson shouldContainExactly forespurtDataListe
                }
            }
        }
    })

private fun mockSpleisForespurtDataListeMedTomtInntektForslag(): List<SpleisForespurtDataDto> =
    listOf(
        SpleisArbeidsgiverperiode,
        SpleisInntekt(
            forslag =
                SpleisForslagInntekt(
                    forrigeInntekt = null,
                ),
        ),
        mockForespurtDataSpleisRefusjon(),
    )

private fun mockSpleisForespurtDataListeMedForrigeInntekt(): List<SpleisForespurtDataDto> =
    listOf(
        SpleisArbeidsgiverperiode,
        SpleisInntekt(
            forslag =
                SpleisForslagInntekt(
                    forrigeInntekt =
                        SpleisForrigeInntekt(
                            skjæringstidspunkt = 1.januar,
                            kilde = "INNTEKTSMELDING",
                            beløp = 10000.0,
                        ),
                ),
        ),
        mockForespurtDataSpleisRefusjon(),
    )
