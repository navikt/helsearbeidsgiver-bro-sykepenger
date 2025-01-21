package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtData
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForrigeInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisArbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForrigeInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForslagInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisRefusjon
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.oktober

class MapForespurtDataKtTest :
    FunSpec({

        beforeTest {
            clearAllMocks()
        }

        context("Arbeidsgiverperiode mappes korrekt") {
            test("Arbeidsgiverperiode påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode =
                            Arbeidsgiverperiode(
                                paakrevd = true,
                            ),
                        inntekt = Inntekt.ikkePaakrevd(),
                        refusjon = Refusjon.ikkePaakrevd(),
                    )

                val spleisForespurtData = listOf(SpleisArbeidsgiverperiode)

                val mappedForespurtData = spleisForespurtData.tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }

            test("Arbeidsgiverperiode _ikke_ påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode =
                            Arbeidsgiverperiode(
                                paakrevd = false,
                            ),
                        inntekt = Inntekt.ikkePaakrevd(),
                        refusjon = Refusjon.ikkePaakrevd(),
                    )

                val mappedForespurtData = emptyList<SpleisForespurtDataDto>().tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }
        }

        context("Inntekt mappes korrekt") {
            test("Inntekt påkrevd (uten forslag)") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode =
                            Arbeidsgiverperiode(
                                paakrevd = false,
                            ),
                        inntekt =
                            Inntekt(
                                paakrevd = true,
                                forslag = null,
                            ),
                        refusjon = Refusjon.ikkePaakrevd(),
                    )

                val spleisForespurtData =
                    listOf(
                        SpleisInntekt(
                            forslag = null,
                        ),
                    )

                val mappedForespurtData = spleisForespurtData.tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }

            test("Inntekt påkrevd (med tomt forslag)") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode =
                            Arbeidsgiverperiode(
                                paakrevd = false,
                            ),
                        inntekt =
                            Inntekt(
                                paakrevd = true,
                                forslag = null,
                            ),
                        refusjon = Refusjon.ikkePaakrevd(),
                    )

                val spleisForespurtData =
                    listOf(
                        SpleisInntekt(
                            forslag = SpleisForslagInntekt(),
                        ),
                    )

                val mappedForespurtData = spleisForespurtData.tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }

            test("Inntekt påkrevd (med forslag)") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode =
                            Arbeidsgiverperiode(
                                paakrevd = false,
                            ),
                        inntekt =
                            Inntekt(
                                paakrevd = true,
                                forslag =
                                    ForslagInntekt(
                                        forrigeInntekt =
                                            ForrigeInntekt(
                                                skjæringstidspunkt = 4.oktober,
                                                kilde = "Imsdal",
                                                beløp = 334.54,
                                            ),
                                    ),
                            ),
                        refusjon = Refusjon.ikkePaakrevd(),
                    )

                val spleisForespurtData =
                    listOf(
                        SpleisInntekt(
                            forslag =
                                SpleisForslagInntekt(
                                    forrigeInntekt =
                                        SpleisForrigeInntekt(
                                            skjæringstidspunkt = 4.oktober,
                                            kilde = "Imsdal",
                                            beløp = 334.54,
                                        ),
                                ),
                        ),
                    )

                val mappedForespurtData = spleisForespurtData.tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }

            test("Inntekt _ikke_ påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode =
                            Arbeidsgiverperiode(
                                paakrevd = false,
                            ),
                        inntekt =
                            Inntekt(
                                paakrevd = false,
                                forslag = null,
                            ),
                        refusjon = Refusjon.ikkePaakrevd(),
                    )

                val mappedForespurtData = emptyList<SpleisForespurtDataDto>().tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }
        }

        context("Refusjon prosesseres og mappes korrekt") {
            withData(
                mapOf(
                    "Refusjon påkrevd, med opphørsdato" to Mock.medOpphoersdato,
                    "Refusjon påkrevd, uten opphørsdato" to Mock.utenOpphoersdato,
                    "Refusjon påkrevd, normale perioder" to Mock.normalePerioder,
                    "Refusjon påkrevd, gap mellom perioder (usortert)" to Mock.gapMellomPerioderUsortert,
                    "Refusjon påkrevd, ikke-siste periode mangler til-dato (usortert)" to Mock.ikkeSistePeriodeManglerTilDatoUsortert,
                ),
            ) {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode =
                            Arbeidsgiverperiode(
                                paakrevd = false,
                            ),
                        inntekt = Inntekt.ikkePaakrevd(),
                        refusjon =
                            Refusjon(
                                paakrevd = true,
                                forslag = it.expectedForslagRefusjon,
                            ),
                    )

                val spleisForespurtData =
                    listOf(
                        SpleisRefusjon(
                            forslag = it.spleisForslagRefusjon,
                        ),
                    )

                val mappedForespurtData = spleisForespurtData.tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }

            test("Refusjon _ikke_ påkrevd") {
                val expectedForespurtData =
                    ForespurtData(
                        arbeidsgiverperiode =
                            Arbeidsgiverperiode(
                                paakrevd = false,
                            ),
                        inntekt = Inntekt.ikkePaakrevd(),
                        refusjon =
                            Refusjon(
                                paakrevd = false,
                                forslag =
                                    ForslagRefusjon(
                                        perioder = emptyList(),
                                        opphoersdato = null,
                                    ),
                            ),
                    )

                val mappedForespurtData = emptyList<SpleisForespurtDataDto>().tilForespurtData()

                mappedForespurtData shouldBe expectedForespurtData
            }
        }
    })

private object Mock {
    val medOpphoersdato =
        TestDataRefusjon(
            spleisForslagRefusjon =
                listOf(
                    SpleisForslagRefusjon(
                        fom = 11.januar,
                        tom = 22.januar,
                        beløp = 111.0,
                    ),
                ),
            expectedForslagRefusjon =
                ForslagRefusjon(
                    perioder =
                        listOf(
                            ForslagRefusjon.Periode(
                                fom = 11.januar,
                                beloep = 111.0,
                            ),
                        ),
                    opphoersdato = 22.januar,
                ),
        )

    val utenOpphoersdato =
        TestDataRefusjon(
            spleisForslagRefusjon =
                listOf(
                    SpleisForslagRefusjon(
                        fom = 21.januar,
                        tom = null,
                        beløp = 222.0,
                    ),
                ),
            expectedForslagRefusjon =
                ForslagRefusjon(
                    perioder =
                        listOf(
                            ForslagRefusjon.Periode(
                                fom = 21.januar,
                                beloep = 222.0,
                            ),
                        ),
                    opphoersdato = null,
                ),
        )

    val normalePerioder =
        TestDataRefusjon(
            spleisForslagRefusjon =
                listOf(
                    SpleisForslagRefusjon(
                        fom = 15.januar,
                        tom = 15.februar,
                        beløp = 10.0,
                    ),
                    SpleisForslagRefusjon(
                        fom = 16.februar,
                        tom = 6.mars,
                        beløp = 20.0,
                    ),
                    SpleisForslagRefusjon(
                        fom = 7.mars,
                        tom = null,
                        beløp = 30.0,
                    ),
                ),
            expectedForslagRefusjon =
                ForslagRefusjon(
                    perioder =
                        listOf(
                            ForslagRefusjon.Periode(
                                fom = 15.januar,
                                beloep = 10.0,
                            ),
                            ForslagRefusjon.Periode(
                                fom = 16.februar,
                                beloep = 20.0,
                            ),
                            ForslagRefusjon.Periode(
                                fom = 7.mars,
                                beloep = 30.0,
                            ),
                        ),
                    opphoersdato = null,
                ),
        )

    val gapMellomPerioderUsortert =
        TestDataRefusjon(
            spleisForslagRefusjon =
                listOf(
                    SpleisForslagRefusjon(
                        fom = 25.januar,
                        tom = 10.februar,
                        beløp = 2.0,
                    ),
                    SpleisForslagRefusjon(
                        fom = 1.januar,
                        tom = 15.januar,
                        beløp = 1.0,
                    ),
                ),
            expectedForslagRefusjon =
                ForslagRefusjon(
                    perioder =
                        listOf(
                            ForslagRefusjon.Periode(
                                fom = 1.januar,
                                beloep = 1.0,
                            ),
                            // Eksplisitt refusjonsopphold lagt til, med beløp 0 kr
                            ForslagRefusjon.Periode(
                                fom = 16.januar,
                                beloep = 0.0,
                            ),
                            ForslagRefusjon.Periode(
                                fom = 25.januar,
                                beloep = 2.0,
                            ),
                        ),
                    opphoersdato = 10.februar,
                ),
        )

    val ikkeSistePeriodeManglerTilDatoUsortert =
        TestDataRefusjon(
            spleisForslagRefusjon =
                listOf(
                    SpleisForslagRefusjon(
                        fom = 5.mars,
                        tom = 20.mars,
                        beløp = 4.0,
                    ),
                    // Mangler til-dato, men er ikke siste element i tid
                    SpleisForslagRefusjon(
                        fom = 11.februar,
                        tom = null,
                        beløp = 3.0,
                    ),
                ),
            expectedForslagRefusjon =
                ForslagRefusjon(
                    perioder =
                        listOf(
                            ForslagRefusjon.Periode(
                                fom = 11.februar,
                                beloep = 3.0,
                            ),
                            ForslagRefusjon.Periode(
                                fom = 5.mars,
                                beloep = 4.0,
                            ),
                        ),
                    opphoersdato = 20.mars,
                ),
        )
}

private class TestDataRefusjon(
    val spleisForslagRefusjon: List<SpleisForslagRefusjon>,
    val expectedForslagRefusjon: ForslagRefusjon,
)
