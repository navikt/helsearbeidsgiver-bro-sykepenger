package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
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

    beforeEach {
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

    test("Legg til eksplisitte til-datoer og refusjonsopphold for refusjonsforslag") {
        val forespoerselUtenEksplisitteRefusjonsforslag = mockForespoerselDto().medRefusjonsforslag(
            ForslagRefusjon(
                fom = 1.januar,
                tom = 15.januar,
                beløp = 1.0
            ),
            // Gap til forrige refusjon
            ForslagRefusjon(
                fom = 25.januar,
                tom = 10.februar,
                beløp = 2.0
            ),
            // Mangler til-dato, men er ikke siste element
            ForslagRefusjon(
                fom = 11.februar,
                tom = null,
                beløp = 3.0
            ),
            ForslagRefusjon(
                fom = 5.mars,
                tom = null,
                beløp = 4.0
            )
        )

        every { mockForespoerselDao.hentAktivForespoerselFor(any()) } returns forespoerselUtenEksplisitteRefusjonsforslag

        val forespoerselMedEksplisitteRefusjonsforslag = forespoerselUtenEksplisitteRefusjonsforslag.medRefusjonsforslag(
            ForslagRefusjon(
                fom = 1.januar,
                tom = 15.januar,
                beløp = 1.0
            ),
            // Eksplisitt refusjonsopphold lagt til, med beløp 0 kr
            ForslagRefusjon(
                fom = 16.januar,
                tom = 24.januar,
                beløp = 0.0
            ),
            ForslagRefusjon(
                fom = 25.januar,
                tom = 10.februar,
                beløp = 2.0
            ),
            // Til-dato lagt til basert på følgende element
            ForslagRefusjon(
                fom = 11.februar,
                tom = 4.mars,
                beløp = 3.0
            ),
            ForslagRefusjon(
                fom = 5.mars,
                tom = null,
                beløp = 4.0
            )
        )

        val expectedPublished = ForespoerselSvar(
            forespoerselId = forespoerselMedEksplisitteRefusjonsforslag.forespoerselId,
            resultat = ForespoerselSvar.Suksess(forespoerselMedEksplisitteRefusjonsforslag),
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

private fun ForespoerselDto.medRefusjonsforslag(vararg forslag: ForslagRefusjon): ForespoerselDto =
    copy(
        forespurtData = forespurtData
            .filterNot { it is Refusjon }
            .plus(
                Refusjon(
                    forslag = forslag.toList()
                )
            )
    )
