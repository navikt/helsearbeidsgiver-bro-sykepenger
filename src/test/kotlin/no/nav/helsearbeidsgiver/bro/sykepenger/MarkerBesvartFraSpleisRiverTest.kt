package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockInntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.time.LocalDateTime
import java.util.UUID

class MarkerBesvartFraSpleisRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
        val mockPriProducer = mockk<PriProducer>(relaxed = true)
        val utsendingstidspunkt = LocalDateTime.of(2024, 6, 1, 12, 0)
        MarkerBesvartFraSpleisRiver(
            rapid = testRapid,
            forespoerselDao = mockForespoerselDao,
            priProducer = mockPriProducer,
        )

        // TODO: Lag tester på flere vedtaksperiodeIder som Liste!
        fun mockInnkommendeMelding(inntektsmeldingHaandtert: InntektsmeldingHaandtertDto) {
            testRapid.sendJson(
                Spleis.Key.TYPE to Spleis.Event.INNTEKTSMELDING_HÅNDTERT.toJson(Spleis.Event.serializer()),
                Spleis.Key.ORGANISASJONSNUMMER to inntektsmeldingHaandtert.orgnr.toJson(),
                Spleis.Key.FØDSELSNUMMER to inntektsmeldingHaandtert.fnr.toJson(),
                Spleis.Key.VEDTAKSPERIODE_ID to inntektsmeldingHaandtert.vedtaksperiodeId.toJson(),
                Spleis.Key.DOKUMENT_ID to inntektsmeldingHaandtert.inntektsmeldingId?.toJson(),
                Spleis.Key.OPPRETTET to inntektsmeldingHaandtert.haandtert.toJson(),
                Spleis.Key.VEDTAKSPERIODE_IDER_MED_SAMME_FRAVAERSDAG to
                    inntektsmeldingHaandtert.vedtaksperiodeIdListe?.toJson(
                        UuidSerializer,
                    ),
            )
        }

        beforeEach {
            clearAllMocks()
        }

        test("Innkommende event oppdaterer aktive forespørsler som er besvart") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = MockUuid.inntektsmeldingId)
            val aktivForespørselId = UUID.randomUUID()
            val aktivOgEksponertForespoersel =
                mockForespoerselDto().copy(
                    forespoerselId = aktivForespørselId,
                    vedtaksperiodeId = inntektsmeldingHaandtert.vedtaksperiodeId,
                )
            every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 1
            every { mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId)) } returns
                listOf(aktivOgEksponertForespoersel)
            every { mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId) } returns
                aktivOgEksponertForespoersel
            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId))
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    inntektsmeldingHaandtert.vedtaksperiodeId,
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
                mockPriProducer.send(aktivForespørselId, *anyVararg())
            }
            verify(exactly = 1) {
                mockPriProducer.send(aktivForespørselId, *anyVararg())
            }
        }

        test("Tåler at dokumentId mangler på innkommende event") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

            every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 1

            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId))
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    inntektsmeldingHaandtert.vedtaksperiodeId,
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
            }
        }

        test("Sier ifra til Simba om besvart forespørsel dersom minst én forespørsel oppdateres") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)
            val expectedForespoerselId = UUID.randomUUID()

            every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 1
            every {
                mockForespoerselDao
                    .hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId))
            } returns listOf(mockForespoerselDto().copy(forespoerselId = expectedForespoerselId))

            mockStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns utsendingstidspunkt
                mockInnkommendeMelding(inntektsmeldingHaandtert)
            }

            verify {
                mockPriProducer.send(
                    expectedForespoerselId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                    Pri.Key.SENDT_TID to utsendingstidspunkt.toJson(),
                )
            }
        }

        test("Sier _ikke_ ifra til Simba om besvart forespørsel dersom ingen forespørsler oppdateres") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

            every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 0
            every { mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId)) } returns
                emptyList()
            every { mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId) } returns null
            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verify(exactly = 0) {
                mockPriProducer.send(any<UUID>(), *anyVararg())
            }
        }

        test("Sender forespørselId-en Simba forventer når forespørsel markeres som besvart") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)
            val expectedForespoerselId = UUID.randomUUID()

            every {
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    inntektsmeldingHaandtert.vedtaksperiodeId,
                    any(),
                    any(),
                )
            } returns 1

            every {
                mockForespoerselDao
                    .hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId))
            } returns listOf(mockForespoerselDto().copy(forespoerselId = expectedForespoerselId))

            mockStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns utsendingstidspunkt
                mockInnkommendeMelding(inntektsmeldingHaandtert)
            }

            verify {
                mockPriProducer.send(
                    expectedForespoerselId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                    Pri.Key.SENDT_TID to utsendingstidspunkt.toJson(),
                )
            }
        }

        test("Videresender inntektsmeldingId når forespørsel markeres som besvart") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = MockUuid.inntektsmeldingId)
            val expectedForespoerselId = UUID.randomUUID()

            every {
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    inntektsmeldingHaandtert.vedtaksperiodeId,
                    any(),
                    any(),
                )
            } returns 1

            every {
                mockForespoerselDao
                    .hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId))
            } returns listOf(mockForespoerselDto().copy(forespoerselId = expectedForespoerselId))

            mockStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns utsendingstidspunkt
                mockInnkommendeMelding(inntektsmeldingHaandtert)
            }

            verify {
                mockPriProducer.send(
                    expectedForespoerselId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                    Pri.Key.SENDT_TID to utsendingstidspunkt.toJson(),
                    Pri.Key.SPINN_INNTEKTSMELDING_ID to MockUuid.inntektsmeldingId.toJson(),
                )
            }
        }
    })
