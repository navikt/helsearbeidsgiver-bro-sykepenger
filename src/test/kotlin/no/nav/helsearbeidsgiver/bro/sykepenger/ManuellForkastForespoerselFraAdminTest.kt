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
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.time.LocalDateTime
import java.util.UUID

class ManuellForkastForespoerselFraAdminTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
        val mockPriProducer = mockk<PriProducer>(relaxed = true)
        val forespoerselId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        val utsendingstidspunkt = LocalDateTime.of(2024, 6, 1, 12, 0)

        beforeEach {
            clearAllMocks()
        }

        test("happy case - finner og forkaster forespørsel og gir beskjed på Pri-topic") {

            every { mockForespoerselDao.hentVedtaksperiodeId(forespoerselId) } returns vedtaksperiodeId
            every { mockForespoerselDao.oppdaterForespoerslerSomForkastet(vedtaksperiodeId) } returns listOf(1L)
            mockStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns utsendingstidspunkt
                ManuellForkastForespoerselFraAdmin(
                    rapid = testRapid,
                    forespoerselDao = mockForespoerselDao,
                    priProducer = mockPriProducer,
                )
                testRapid.sendJson(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_MANUELT_FORKASTET.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(UuidSerializer),
                )
                verifySequence {
                    mockForespoerselDao.hentVedtaksperiodeId(forespoerselId)
                    mockForespoerselDao.oppdaterForespoerslerSomForkastet(vedtaksperiodeId)
                    mockPriProducer.send(
                        vedtaksperiodeId,
                        Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FORKASTET.toJson(Pri.NotisType.serializer()),
                        Pri.Key.SENDT_TID to utsendingstidspunkt.truncMillis().toJson(),
                        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    )
                    mockPriProducer.send(
                        vedtaksperiodeId,
                        Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID.toJson(Pri.BehovType.serializer()),
                        Pri.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toJson(),
                    )
                }
            }
        }

        test("ukjent forespørsel gjør ingenting og sender ikke noe på Pri-topic") {

            every { mockForespoerselDao.hentVedtaksperiodeId(forespoerselId) } returns null
            ManuellForkastForespoerselFraAdmin(
                rapid = testRapid,
                forespoerselDao = mockForespoerselDao,
                priProducer = mockPriProducer,
            )
            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_MANUELT_FORKASTET.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(UuidSerializer),
            )

            verify(exactly = 0) {
                mockForespoerselDao.oppdaterForespoerslerSomForkastet(any())
                mockPriProducer.send(any<UUID>(), *anyVararg())
            }
        }

        test("Allerede besvart forespørsel oppdateres ikke og sender ikke noe på Pri-topic") {

            every { mockForespoerselDao.hentVedtaksperiodeId(forespoerselId) } returns vedtaksperiodeId
            every { mockForespoerselDao.oppdaterForespoerslerSomForkastet(vedtaksperiodeId) } returns emptyList()
            ManuellForkastForespoerselFraAdmin(
                rapid = testRapid,
                forespoerselDao = mockForespoerselDao,
                priProducer = mockPriProducer,
            )
            testRapid.sendJson(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_MANUELT_FORKASTET.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(UuidSerializer),
            )

            verify(exactly = 0) {

                mockPriProducer.send(any<UUID>(), *anyVararg())
            }
        }
    })
