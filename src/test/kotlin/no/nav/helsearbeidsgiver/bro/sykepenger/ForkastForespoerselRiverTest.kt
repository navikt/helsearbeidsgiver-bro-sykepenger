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
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.time.LocalDateTime
import java.util.UUID

class ForkastForespoerselRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
        val mockPriProducer = mockk<PriProducer>(relaxed = true)

        ForkastForespoerselRiver(
            rapid = testRapid,
            forespoerselDao = mockForespoerselDao,
            priProducer = mockPriProducer,
        )

        fun mockForkastForespoerselMelding(vedtaksperiodeId: UUID) {
            testRapid.sendJson(
                Spleis.Key.TYPE to Spleis.Event.TRENGER_IKKE_OPPLYSNINGER_FRA_ARBEIDSGIVER.toJson(Spleis.Event.serializer()),
                Spleis.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toJson(),
            )
        }

        beforeEach {
            clearAllMocks()
            testRapid.reset()
        }

        test("Innkommende event oppdaterer aktive forespørsler som forkastet") {
            mockForkastForespoerselMelding(vedtaksperiodeId)

            verifySequence {
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId)
                mockForespoerselDao.oppdaterForespoerslerSomForkastet(vedtaksperiodeId)
            }
        }

        test("Sier ifra til Simba om at forespørsel er forkastet") {
            val forespoersel = mockForespoerselDto()
            val utesendingstidspunkt = LocalDateTime.of(2024, 6, 1, 12, 0)
            mockStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns utesendingstidspunkt

                every {
                    mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId)
                } returns forespoersel

                mockForkastForespoerselMelding(vedtaksperiodeId)

                verifySequence {
                    mockPriProducer.sendWithKey(
                        vedtaksperiodeId.toString(),
                        Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FORKASTET.toJson(Pri.NotisType.serializer()),
                        Pri.Key.SENDT_TID to utesendingstidspunkt.toJson(),
                        Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
                    )
                }
            }
        }

        test("Sier _ikke_ ifra til Simba om at forespørsel er forkastet dersom vi ikke finner en aktiv forespørsel") {

            every {
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId)
            } returns null

            mockForkastForespoerselMelding(vedtaksperiodeId)

            verify(exactly = 0) {
                mockPriProducer.sendWithKey(any(), *anyVararg())
            }
        }
    })
