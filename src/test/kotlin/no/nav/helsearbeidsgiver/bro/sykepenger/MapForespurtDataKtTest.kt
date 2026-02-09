package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtData
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisArbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisFastsattInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisRefusjon

class MapForespurtDataKtTest :
    FunSpec({

        beforeTest {
            clearAllMocks()
        }

        test("Alle opplysninger påkrevd") {
            val expectedForespurtData =
                ForespurtData(
                    arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = true),
                    inntekt = Inntekt(paakrevd = true),
                    refusjon = Refusjon(paakrevd = true),
                )

            val mappedForespurtData = setOf(SpleisArbeidsgiverperiode, SpleisInntekt, SpleisRefusjon).tilForespurtData()

            mappedForespurtData shouldBe expectedForespurtData
        }

        test("Ingen opplysninger påkrevd (skal aldri skje i praksis)") {
            val expectedForespurtData =
                ForespurtData(
                    arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = false),
                    inntekt = Inntekt(paakrevd = false),
                    refusjon = Refusjon(paakrevd = false),
                )

            val mappedForespurtData = emptySet<SpleisForespurtDataDto>().tilForespurtData()

            mappedForespurtData shouldBe expectedForespurtData
        }

        test("Utdatert 'FastsattInntekt' ignoreres") {
            val expectedForespurtData =
                ForespurtData(
                    arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = true),
                    inntekt = Inntekt(paakrevd = false),
                    refusjon = Refusjon(paakrevd = true),
                )

            val mappedForespurtData = setOf(SpleisArbeidsgiverperiode, SpleisFastsattInntekt, SpleisRefusjon).tilForespurtData()

            mappedForespurtData shouldBe expectedForespurtData
        }

        context("Arbeidsgiverperiode mappes korrekt") {
            test("Arbeidsgiverperiode påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = true),
                        inntekt = Inntekt(paakrevd = false),
                        refusjon = Refusjon(paakrevd = false),
                    )

                val spleisForespurtData = setOf(SpleisArbeidsgiverperiode)

                val mappedForespurtData = spleisForespurtData.tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }

            test("Arbeidsgiverperiode _ikke_ påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = false),
                        inntekt = Inntekt(paakrevd = true),
                        refusjon = Refusjon(paakrevd = true),
                    )

                val mappedForespurtData = setOf(SpleisInntekt, SpleisRefusjon).tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }
        }

        context("Inntekt mappes korrekt") {
            test("Inntekt påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = false),
                        inntekt = Inntekt(paakrevd = true),
                        refusjon = Refusjon(paakrevd = false),
                    )

                val spleisForespurtData = setOf(SpleisInntekt)

                val mappedForespurtData = spleisForespurtData.tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }

            test("Inntekt _ikke_ påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = true),
                        inntekt = Inntekt(paakrevd = false),
                        refusjon = Refusjon(paakrevd = true),
                    )

                val mappedForespurtData = setOf(SpleisArbeidsgiverperiode, SpleisRefusjon).tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }
        }

        context("Refusjon prosesseres og mappes korrekt") {
            test("Refusjon påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = false),
                        inntekt = Inntekt(paakrevd = false),
                        refusjon = Refusjon(paakrevd = true),
                    )

                val mappedForespurtData = setOf(SpleisRefusjon).tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }

            test("Refusjon _ikke_ påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = true),
                        inntekt = Inntekt(paakrevd = true),
                        refusjon = Refusjon(paakrevd = false),
                    )

                val mappedForespurtData = setOf(SpleisArbeidsgiverperiode, SpleisInntekt).tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }
        }
    })
