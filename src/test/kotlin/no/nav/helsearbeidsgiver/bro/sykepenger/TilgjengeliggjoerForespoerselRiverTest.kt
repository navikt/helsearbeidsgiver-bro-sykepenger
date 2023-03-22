package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockJsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson

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
