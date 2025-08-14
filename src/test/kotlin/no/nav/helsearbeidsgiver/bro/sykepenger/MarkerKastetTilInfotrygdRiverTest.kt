package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class MarkerKastetTilInfotrygdRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
        val mockPriProducer = mockk<PriProducer>(relaxed = true)

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

        beforeTest {
            clearAllMocks()
        }

        test("Oppdaterer database og sender melding til Simba ved aktiv forespørsel") {
            val mockForespoersel = mockForespoerselDto().copy(status = Status.AKTIV)

            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(mockForespoersel.vedtaksperiodeId))
            } returns listOf(mockForespoersel)

            mockMarkerKastetTilInfotrygdMelding(mockForespoersel.vedtaksperiodeId)

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(mockForespoersel.vedtaksperiodeId))
                mockForespoerselDao.markerKastetTilInfotrygd(mockForespoersel.vedtaksperiodeId)
                mockPriProducer.send(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_KASTET_TIL_INFOTRYGD.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to mockForespoersel.forespoerselId.toJson(),
                )
            }
        }

        context("Oppdaterer database, men sender _ikke_ melding til Simba ved besvart forespørsel") {
            withData(
                Status.BESVART_SIMBA,
                Status.BESVART_SPLEIS,
            ) { besvartStatus ->
                val mockForespoersel = mockForespoerselDto().copy(status = besvartStatus)

                every {
                    mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(mockForespoersel.vedtaksperiodeId))
                } returns listOf(mockForespoersel)

                mockMarkerKastetTilInfotrygdMelding(mockForespoersel.vedtaksperiodeId)

                verifySequence {
                    mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(mockForespoersel.vedtaksperiodeId))
                    mockForespoerselDao.markerKastetTilInfotrygd(mockForespoersel.vedtaksperiodeId)
                }
                verify(exactly = 0) {
                    mockPriProducer.send(*anyVararg())
                }
            }
        }

        test("Hverken oppdaterer database eller sender melding til Simba dersom vi ikke finner forespørsler for vedtaksperiode-ID") {
            val vedtaksperiodeId = UUID.randomUUID()

            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vedtaksperiodeId))
            } returns emptyList()

            mockMarkerKastetTilInfotrygdMelding(vedtaksperiodeId)

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vedtaksperiodeId))
            }
            verify(exactly = 0) {
                mockForespoerselDao.markerKastetTilInfotrygd(any())
                mockPriProducer.send(any(), any())
            }
        }
    })
