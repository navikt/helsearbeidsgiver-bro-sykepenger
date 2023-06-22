package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.RefusjonPeriode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.februar
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.januar
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mars
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockBegrensetForespurtDataListe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockJsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson

class TilgjengeliggjoerForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>()
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    TilgjengeliggjoerForespoerselRiver(testRapid, mockForespoerselDao, mockPriProducer)

    beforeTest {
        clearAllMocks()
    }

    test("Ved innkommende event, svar ut korrekt ForespoerselSvar") {
        val forespoersel = mockForespoerselDto()

        every { mockForespoerselDao.hentAktivForespoerselFor(any()) } returns forespoersel

        val expectedPublished = ForespoerselSvar(
            forespoerselId = forespoersel.forespoerselId,
            resultat = ForespoerselSvar.Suksess(forespoersel),
            boomerang = mockJsonElement()
        )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
            Pri.Key.FORESPOERSEL_ID to expectedPublished.forespoerselId.toJson(),
            Pri.Key.BOOMERANG to expectedPublished.boomerang
        )

        verifySequence {
            mockForespoerselDao.hentAktivForespoerselFor(any())
            mockPriProducer.send(
                Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedPublished.toJson(ForespoerselSvar.serializer())
            )
        }
    }

    test("Ved innkommende event, svar ut korrekt ForespoerselSvar med begrenset forespurtData og uten skjæringstidspunkt") {
        val forespoersel = mockForespoerselDto().copy(
            type = Type.BEGRENSET,
            skjaeringstidspunkt = null,
            forespurtData = mockBegrensetForespurtDataListe()
        )

        every { mockForespoerselDao.hentAktivForespoerselFor(any()) } returns forespoersel

        val expectedPublished = ForespoerselSvar(
            forespoerselId = forespoersel.forespoerselId,
            resultat = ForespoerselSvar.Suksess(forespoersel),
            boomerang = mockJsonElement()
        )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
            Pri.Key.FORESPOERSEL_ID to expectedPublished.forespoerselId.toJson(),
            Pri.Key.BOOMERANG to expectedPublished.boomerang
        )

        verifySequence {
            mockForespoerselDao.hentAktivForespoerselFor(any())
            mockPriProducer.send(
                Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedPublished.toJson(ForespoerselSvar.serializer())
            )
        }
    }

    context("Erstatter refusjonsforslag fra spleis med eget refusjonsforslag") {
        withData(
            mapOf(
                "Med opphørsdato" to Mock.medOpphoersdato,
                "Uten opphørsdato" to Mock.utenOpphoersdato,
                "Normale perioder" to Mock.normalePerioder,
                "Gap mellom perioder (usortert)" to Mock.gapMellomPerioderUsortert,
                "Ikke-siste periode mangler til-dato (usortert)" to Mock.ikkeSistePeriodeManglerTilDatoUsortert
            )
        ) {
            val forespoerselMedRefusjonsforslagFraSpleis = mockForespoerselDto().erstattRefusjon(it.fraSpleis)

            every { mockForespoerselDao.hentAktivForespoerselFor(any()) } returns forespoerselMedRefusjonsforslagFraSpleis

            val forespoerselMedEgetRefusjonsforslag = forespoerselMedRefusjonsforslagFraSpleis.erstattRefusjon(it.expectedEgendefinert)

            val expectedPublished = ForespoerselSvar(
                forespoerselId = forespoerselMedEgetRefusjonsforslag.forespoerselId,
                resultat = ForespoerselSvar.Suksess(forespoerselMedEgetRefusjonsforslag),
                boomerang = mockJsonElement()
            )

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                Pri.Key.FORESPOERSEL_ID to expectedPublished.forespoerselId.toJson(),
                Pri.Key.BOOMERANG to expectedPublished.boomerang
            )

            verifySequence {
                mockPriProducer.send(
                    Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
                    Pri.Key.LØSNING to expectedPublished.toJson(ForespoerselSvar.serializer())
                )
            }
        }
    }

    test("Når forespørsel ikke finnes skal det sendes ForespoerselSvar med error") {
        every { mockForespoerselDao.hentAktivForespoerselFor(any()) } returns null

        val forespoersel = mockForespoerselDto()
        val expectedPublished = ForespoerselSvar(
            forespoerselId = forespoersel.forespoerselId,
            feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET,
            boomerang = mockJsonElement()
        )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
            Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
            Pri.Key.BOOMERANG to expectedPublished.boomerang
        )

        verifySequence {
            mockForespoerselDao.hentAktivForespoerselFor(any())
            mockPriProducer.send(
                Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedPublished.toJson(ForespoerselSvar.serializer())
            )
        }
    }
})

private fun ForespoerselDto.erstattRefusjon(refusjon: ForespurtDataDto): ForespoerselDto =
    copy(
        forespurtData = forespurtData
            .filterNot { it is Refusjon || it is SpleisRefusjon }
            .plus(refusjon)
    )

private object Mock {
    val medOpphoersdato = TestDataRefusjon(
        fraSpleis = SpleisRefusjon(
            forslag = listOf(
                SpleisForslagRefusjon(
                    fom = 11.januar,
                    tom = 22.januar,
                    beløp = 111.0
                )
            )
        ),
        expectedEgendefinert = Refusjon(
            forslag = ForslagRefusjon(
                perioder = listOf(
                    RefusjonPeriode(
                        fom = 11.januar,
                        beloep = 111.0
                    )
                ),
                opphoersdato = 22.januar
            )
        )
    )

    val utenOpphoersdato = TestDataRefusjon(
        fraSpleis = SpleisRefusjon(
            forslag = listOf(
                SpleisForslagRefusjon(
                    fom = 21.januar,
                    tom = null,
                    beløp = 222.0
                )
            )
        ),
        expectedEgendefinert = Refusjon(
            forslag = ForslagRefusjon(
                perioder = listOf(
                    RefusjonPeriode(
                        fom = 21.januar,
                        beloep = 222.0
                    )
                ),
                opphoersdato = null
            )
        )
    )

    val normalePerioder = TestDataRefusjon(
        fraSpleis = SpleisRefusjon(
            forslag = listOf(
                SpleisForslagRefusjon(
                    fom = 15.januar,
                    tom = 15.februar,
                    beløp = 10.0
                ),
                SpleisForslagRefusjon(
                    fom = 16.februar,
                    tom = 6.mars,
                    beløp = 20.0
                ),
                SpleisForslagRefusjon(
                    fom = 7.mars,
                    tom = null,
                    beløp = 30.0
                )
            )
        ),
        expectedEgendefinert = Refusjon(
            forslag = ForslagRefusjon(
                perioder = listOf(
                    RefusjonPeriode(
                        fom = 15.januar,
                        beloep = 10.0
                    ),
                    RefusjonPeriode(
                        fom = 16.februar,
                        beloep = 20.0
                    ),
                    RefusjonPeriode(
                        fom = 7.mars,
                        beloep = 30.0
                    )
                ),
                opphoersdato = null
            )
        )
    )

    val gapMellomPerioderUsortert = TestDataRefusjon(
        fraSpleis = SpleisRefusjon(
            forslag = listOf(
                SpleisForslagRefusjon(
                    fom = 25.januar,
                    tom = 10.februar,
                    beløp = 2.0
                ),
                SpleisForslagRefusjon(
                    fom = 1.januar,
                    tom = 15.januar,
                    beløp = 1.0
                )
            )
        ),
        expectedEgendefinert = Refusjon(
            forslag = ForslagRefusjon(
                perioder = listOf(
                    RefusjonPeriode(
                        fom = 1.januar,
                        beloep = 1.0
                    ),
                    // Eksplisitt refusjonsopphold lagt til, med beløp 0 kr
                    RefusjonPeriode(
                        fom = 16.januar,
                        beloep = 0.0
                    ),
                    RefusjonPeriode(
                        fom = 25.januar,
                        beloep = 2.0
                    )
                ),
                opphoersdato = 10.februar
            )
        )
    )

    val ikkeSistePeriodeManglerTilDatoUsortert = TestDataRefusjon(
        fraSpleis = SpleisRefusjon(
            forslag = listOf(
                SpleisForslagRefusjon(
                    fom = 5.mars,
                    tom = 20.mars,
                    beløp = 4.0
                ),
                // Mangler til-dato, men er ikke siste element i tid
                SpleisForslagRefusjon(
                    fom = 11.februar,
                    tom = null,
                    beløp = 3.0
                )
            )
        ),

        expectedEgendefinert = Refusjon(
            forslag = ForslagRefusjon(
                perioder = listOf(
                    RefusjonPeriode(
                        fom = 11.februar,
                        beloep = 3.0
                    ),
                    RefusjonPeriode(
                        fom = 5.mars,
                        beloep = 4.0
                    )
                ),
                opphoersdato = 20.mars
            )
        )
    )
}

private class TestDataRefusjon(
    val fraSpleis: SpleisRefusjon,
    val expectedEgendefinert: Refusjon
)
