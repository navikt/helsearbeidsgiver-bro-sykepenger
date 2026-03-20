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

        fun mockInnkommendeMelding(inntektsmeldingHaandtert: InntektsmeldingHaandtertDto) {
            testRapid.sendJson(
                Spleis.Key.TYPE to Spleis.Event.INNTEKTSMELDING_HÅNDTERT.toJson(Spleis.Event.serializer()),
                Spleis.Key.ORGANISASJONSNUMMER to inntektsmeldingHaandtert.orgnr.toJson(),
                Spleis.Key.FØDSELSNUMMER to inntektsmeldingHaandtert.fnr.toJson(),
                Spleis.Key.DOKUMENT_ID to inntektsmeldingHaandtert.inntektsmeldingId?.toJson(),
                Spleis.Key.OPPRETTET to inntektsmeldingHaandtert.haandtert.toJson(),
                Spleis.Key.VEDTAKSPERIODE_IDER_MED_SAMME_FRAVAERSDAG to
                    inntektsmeldingHaandtert.vedtaksperiodeIdListe.toJson(
                        UuidSerializer,
                    ),
            )
        }

        beforeEach {
            clearAllMocks()
        }
        test("Innkommende event oppdaterer én aktiv (og eksponert) forespørsel som er besvart, sender én melding") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = MockUuid.inntektsmeldingId)
            val aktivForespoerselId = UUID.randomUUID()
            val aktivOgEksponertForespoersel =
                mockForespoerselDto().copy(
                    forespoerselId = aktivForespoerselId,
                    vedtaksperiodeId = inntektsmeldingHaandtert.vedtaksperiodeIdListe.first(),
                )
            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(
                    inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet(),
                )
            } returns
                listOf(aktivOgEksponertForespoersel)
            every {
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeIdListe.first())
            } returns
                aktivOgEksponertForespoersel
            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet())
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeIdListe.first())
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    aktivOgEksponertForespoersel.vedtaksperiodeId,
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
                mockPriProducer.send(aktivForespoerselId, *anyVararg())
            }
            verify(exactly = 1) {
                mockPriProducer.send(aktivForespoerselId, *anyVararg())
            }
        }

        test("Tåler at dokumentId mangler på innkommende event") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet())
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeIdListe.first())
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    inntektsmeldingHaandtert.vedtaksperiodeIdListe.first(),
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
            }
        }

        test("Sier ifra til Simba om besvarte forespørsler dersom flere enn én forespørsel oppdateres") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)
            val eksponertForespoerselId = UUID.randomUUID()
            val aktivForespoerselId = UUID.randomUUID()

            every {
                mockForespoerselDao
                    .hentForespoerslerEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet())
            } returns listOf(mockForespoerselDto().copy(forespoerselId = eksponertForespoerselId))
            every {
                mockForespoerselDao
                    .hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeIdListe.first())
            } returns mockForespoerselDto().copy(forespoerselId = aktivForespoerselId)
            mockStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns utsendingstidspunkt
                mockInnkommendeMelding(inntektsmeldingHaandtert)
            }

            verify {
                mockPriProducer.send(
                    eksponertForespoerselId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to eksponertForespoerselId.toJson(),
                    Pri.Key.SENDT_TID to utsendingstidspunkt.toJson(),
                )
                mockPriProducer.send(
                    aktivForespoerselId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to aktivForespoerselId.toJson(),
                    Pri.Key.SENDT_TID to utsendingstidspunkt.toJson(),
                )
            }
        }

        test("Sier _ikke_ ifra til Simba om besvart forespørsel dersom ingen forespørsler oppdateres") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(
                    inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet(),
                )
            } returns
                emptyList()
            every {
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeIdListe.first())
            } returns
                null
            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verify(exactly = 0) {
                mockPriProducer.send(any<UUID>(), *anyVararg())
            }
        }

        test("Videresender inntektsmeldingId når forespørsel markeres som besvart") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = MockUuid.inntektsmeldingId)
            val expectedForespoerselId = UUID.randomUUID()

            every {
                mockForespoerselDao
                    .hentForespoerslerEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet())
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

        test("Innkommende event der en ikke-aktiv forespørsel matcher") {
            val eksponertForespoerselId = UUID.randomUUID()
            val vedtaksperiodeSomMatcher = UUID.randomUUID()
            val inntektsmeldingHaandtert =
                mockInntektsmeldingHaandtertDto(dokumentId = MockUuid.inntektsmeldingId).copy(
                    vedtaksperiodeIdListe = listOf(MockUuid.vedtaksperiodeId, vedtaksperiodeSomMatcher),
                )
            val aktivOgEksponertForespoersel =
                mockForespoerselDto().copy(
                    forespoerselId = eksponertForespoerselId,
                    vedtaksperiodeId = vedtaksperiodeSomMatcher,
                )
            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(
                    inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet(),
                )
            } returns
                listOf(aktivOgEksponertForespoersel)
            every { mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(any()) } returns null
            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet())
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeIdListe.first())
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeSomMatcher)
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    inntektsmeldingHaandtert.vedtaksperiodeIdListe.first(),
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    vedtaksperiodeSomMatcher,
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
            }
            verify(exactly = 1) {
                mockPriProducer.send(eksponertForespoerselId, *anyVararg())
            }
        }

        test("Innkommende event med mange perioder") {
            val eksponertForespoerselId = UUID.randomUUID()
            val aktivForespoerselId = UUID.randomUUID()
            val vedtaksperiodeSomMatcher = UUID.randomUUID()
            val inntektsmeldingHaandtert =
                mockInntektsmeldingHaandtertDto(dokumentId = MockUuid.inntektsmeldingId).copy(
                    vedtaksperiodeIdListe = listOf(MockUuid.vedtaksperiodeId, vedtaksperiodeSomMatcher, UUID.randomUUID()),
                )

            val aktivForespoersel =
                mockForespoerselDto().copy(
                    forespoerselId = aktivForespoerselId,
                    vedtaksperiodeId = vedtaksperiodeSomMatcher,
                )
            val eksponertForespoersel =
                mockForespoerselDto().copy(
                    forespoerselId = eksponertForespoerselId,
                    vedtaksperiodeId = vedtaksperiodeSomMatcher,
                )
            every {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(
                    inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet(),
                )
            } returns
                listOf(aktivForespoersel, eksponertForespoersel)
            every { mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeSomMatcher) } returns aktivForespoersel
            every { mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(eksponertForespoerselId) } returns aktivForespoersel
            every { mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(any()) } returns null
            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verifySequence {
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeIdListe.toSet())
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeIdListe.first())
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeSomMatcher)
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(any())
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    inntektsmeldingHaandtert.vedtaksperiodeIdListe.first(),
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    vedtaksperiodeSomMatcher,
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    any(),
                    any(),
                    any(),
                )
            }
            verify(exactly = 2) {
                mockPriProducer.send(any(), *anyVararg())
            }
        }
    })
