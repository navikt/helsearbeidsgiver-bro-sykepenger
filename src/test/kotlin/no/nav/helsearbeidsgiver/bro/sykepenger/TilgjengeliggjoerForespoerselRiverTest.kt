package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
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

    test("Ved innkommende event, svar ut korrekt ForespoerselSvar") {
        val forespoersel = mockForespoerselDto()

        every { mockForespoerselDao.hentAktivForespoerselFor(any()) } returns forespoersel

        val expectedPublished = ForespoerselSvar(
            forespoersel = forespoersel,
            boomerang = mapOf(
                Pri.Key.BOOMERANG.str to "boomyrangy".toJson()
            )
        )

        testRapid.sendJson(
            Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(),
            Pri.Key.ORGNR to expectedPublished.orgnr.toJson(),
            Pri.Key.FNR to expectedPublished.fnr.toJson(),
            Pri.Key.VEDTAKSPERIODE_ID to expectedPublished.vedtaksperiodeId.toJson(),
            Pri.Key.BOOMERANG to expectedPublished.boomerang.toJson()
        )

        verifySequence {
            mockForespoerselDao.hentAktivForespoerselFor(any())
            mockPriProducer.send(expectedPublished, ForespoerselSvar::toJson)
        }
    }
})
