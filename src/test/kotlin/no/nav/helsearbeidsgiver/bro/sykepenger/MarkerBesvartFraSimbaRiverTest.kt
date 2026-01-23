package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class MarkerBesvartFraSimbaRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)

        MarkerBesvartFraSimbaRiver(
            rapid = testRapid,
            forespoerselDao = mockForespoerselDao,
        )

        fun mockInnkommendeMelding(forespoerselId: UUID) {
            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(UuidSerializer),
            )
        }

        beforeEach {
            clearAllMocks()
        }

        test("Innkommende event oppdaterer aktive forespørsler som besvart") {
            val forespoerselId = UUID.randomUUID()
            val vedtaksperiodeId = UUID.randomUUID()

            every { mockForespoerselDao.hentVedtaksperiodeId(forespoerselId) } returns vedtaksperiodeId

            mockInnkommendeMelding(forespoerselId)

            verifySequence {
                mockForespoerselDao.hentVedtaksperiodeId(forespoerselId)
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(vedtaksperiodeId, any())
            }
        }

        test("Innkommende event gjør ingenting dersom forespørsel ikke finnes") {
            val forespoerselId = UUID.randomUUID()

            every { mockForespoerselDao.hentVedtaksperiodeId(forespoerselId) } returns null

            mockInnkommendeMelding(forespoerselId)

            verifySequence {
                mockForespoerselDao.hentVedtaksperiodeId(forespoerselId)
            }
            verify(exactly = 0) {
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(any(), any())
            }
        }
    })
