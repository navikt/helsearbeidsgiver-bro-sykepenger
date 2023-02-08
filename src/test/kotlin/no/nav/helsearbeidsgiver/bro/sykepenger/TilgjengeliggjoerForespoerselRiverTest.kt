package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvarFeil
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvarSuksess
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
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

        val expectedResultat = ForespoerselSvarSuksess(
            forespoersel = forespoersel
        )
        val expectedPublished = ForespoerselSvar(
            forespoerselId = forespoersel.forespoerselId,
            resultat = expectedResultat,
            boomerang = mapOf(
                Pri.Key.BOOMERANG.str to "boomyrangy".toJson()
            )
        )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(),
            Pri.Key.ORGNR to expectedResultat.orgnr.toJson(),
            Pri.Key.FNR to expectedResultat.fnr.toJson(),
            Pri.Key.FORESPOERSEL_ID to expectedPublished.forespoerselId.toJson(),
            Pri.Key.BOOMERANG to expectedPublished.boomerang.toJson()
        )

        verifySequence {
            mockForespoerselDao.hentAktivForespoerselFor(any())
            mockPriProducer.send(expectedPublished, ForespoerselSvar::toJson)
        }
    }

    test("Når forespørsel ikke finnes skal det sendes ForespoerselSvar med error") {
        every { mockForespoerselDao.hentAktivForespoerselFor(any()) } returns null

        val forespoersel = mockForespoerselDto()
        val expectedPublished = ForespoerselSvar(
            forespoerselId = forespoersel.forespoerselId,
            feil = ForespoerselSvarFeil.FORESPOERSEL_IKKE_FUNNET,
            boomerang = mapOf(
                Pri.Key.BOOMERANG.str to "boomyrangy".toJson()
            )
        )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(),
            Pri.Key.ORGNR to forespoersel.orgnr.toJson(),
            Pri.Key.FNR to forespoersel.fnr.toJson(),
            Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
            Pri.Key.BOOMERANG to expectedPublished.boomerang.toJson()
        )

        verifySequence {
            mockForespoerselDao.hentAktivForespoerselFor(any())
            mockPriProducer.send(expectedPublished, ForespoerselSvar::toJson)
        }
    }
})
