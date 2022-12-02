package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.MOCK_UUID
import java.util.UUID

class ForespurtDataRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    ForespurtDataRiver(testRapid, mockForespoerselDao, mockPriProducer)

    test("!Ved innkommende event, svar ut korrekt forespurtData") {
        val expected = TrengerForespurtData(
            fnr = "fnr",
            orgnr = "orgnr",
            vedtaksperiodeId = UUID.fromString(MOCK_UUID)
        )

        testRapid.sendJson(
            Key.EVENT_TYPE to EVENT_TYPE.tryToJson(),
            Key.ORGNR to expected.orgnr.tryToJson(),
            Key.FØDSELSNUMMER to expected.fnr.tryToJson(),
            Key.VEDTAKSPERIODE_ID to expected.vedtaksperiodeId.toString().tryToJson()
        )

        verifySequence {
            mockForespoerselDao.hentAktivForespørselFor(any())
            mockPriProducer.send(expected, any())
        }
    }
})
