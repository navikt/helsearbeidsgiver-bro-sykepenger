package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespoerselDto

class TilgjengeliggjoerForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>()
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    TilgjengeliggjoerForespoerselRiver(testRapid, mockForespoerselDao, mockPriProducer)

    test("Ved innkommende event, svar ut korrekt ForespoerselSvar") {
        val forespoersel = mockForespoerselDto()
        every { mockForespoerselDao.hentAktivForespørselFor(any()) } returns forespoersel
        val expected = ForespoerselSvar(forespoersel)

        testRapid.sendJson(
            Key.EVENT_TYPE to Event.TRENGER_FORESPOERSEL.tryToJson(),
            Key.ORGNR to expected.orgnr.tryToJson(),
            Key.FNR to expected.fnr.tryToJson(),
            Key.VEDTAKSPERIODE_ID to expected.vedtaksperiodeId.toString().tryToJson()
        )

        verifySequence {
            mockForespoerselDao.hentAktivForespørselFor(any())
            mockPriProducer.send(expected, any())
        }
    }
})
