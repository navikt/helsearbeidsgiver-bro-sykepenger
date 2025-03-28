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
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid.vedtaksperiodeId
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class MarkerKastetTilInfotrygdRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
        val mockPriProducer = mockk<PriProducer>(relaxed = true)

        val mockForespoersel = mockForespoerselDto()

        MarkerKastetTilInfotrygdRiver(
            rapid = testRapid,
            forespoerselDao = mockForespoerselDao,
            priProducer = mockPriProducer,
        )

        fun mockMarkerKastetTilInfotrygdMelding(vedtaksperiodeId: UUID) {
            testRapid.sendJson(
                Spleis.Key.TYPE to Spleis.Event.VEDTAKSPERIODE_FORKASTET.toJson(Spleis.Event.serializer()),
                Spleis.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toJson(),
            )
        }

        beforeEach {
            clearAllMocks()
        }

        test("Innkommende event markerer vedtaksperiode kastet til infotrygd") {
            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vedtaksperiodeId))
            } returns listOf(mockForespoersel)

            mockMarkerKastetTilInfotrygdMelding(vedtaksperiodeId)

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vedtaksperiodeId))
                mockForespoerselDao.markerKastetTilInfotrygd(vedtaksperiodeId)
            }
        }

        test("Sier ifra til Simba om at påminnelse for forespørsel skal avbestilles") {
            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vedtaksperiodeId))
            } returns listOf(mockForespoersel)

            mockMarkerKastetTilInfotrygdMelding(vedtaksperiodeId)

            verifySequence {
                mockPriProducer.send(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_KASTET_TIL_INFOTRYGD.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to mockForespoersel.forespoerselId.toJson(),
                )
            }
        }

        test(
            "Sier ikke ifra til Simba om at påminnelse for forespørsel skal avbestilles dersom vi ikke finner forespørsler for vedtaksperiode-id",
        ) {
            mockMarkerKastetTilInfotrygdMelding(vedtaksperiodeId)

            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vedtaksperiodeId))
            } returns emptyList()

            verify(exactly = 0) {
                mockPriProducer.send(any(), any())
            }
        }

        test(
            "Markerer ikke vedtaksperiode kastet til infotrygd dersom vi ikke finner forespørsler for vedtaksperiode-id",
        ) {
            mockMarkerKastetTilInfotrygdMelding(vedtaksperiodeId)

            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vedtaksperiodeId))
            } returns emptyList()

            verify(exactly = 0) {
                mockForespoerselDao.markerKastetTilInfotrygd(any())
            }
        }
    })
