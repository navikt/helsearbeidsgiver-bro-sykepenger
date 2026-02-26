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
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockInntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDateTime
import java.util.UUID

class MarkerBesvartFraSpleisRiverTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
        val mockPriProducer = mockk<PriProducer>(relaxed = true)
        val utesendingstidspunkt = LocalDateTime.of(2024, 6, 1, 12, 0)
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
                Spleis.Key.VEDTAKSPERIODE_ID to inntektsmeldingHaandtert.vedtaksperiodeId.toJson(),
                Spleis.Key.DOKUMENT_ID to inntektsmeldingHaandtert.inntektsmeldingId?.toJson(),
                Spleis.Key.OPPRETTET to inntektsmeldingHaandtert.haandtert.toJson(),
            )
        }

        beforeEach {
            clearAllMocks()
        }

        test("Innkommende event oppdaterer aktive forespørsler som er besvart") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = MockUuid.inntektsmeldingId)

            every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 1

            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verifySequence {
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    inntektsmeldingHaandtert.vedtaksperiodeId,
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId))
            }
        }

        test("Tåler at dokumentId mangler på innkommende event") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

            every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 1

            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verifySequence {
                mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)
                mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    inntektsmeldingHaandtert.vedtaksperiodeId,
                    inntektsmeldingHaandtert.haandtert,
                    inntektsmeldingHaandtert.inntektsmeldingId,
                )
                mockForespoerselDao.hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId))
            }
        }

        test("Sier ifra til Simba om besvart forespørsel dersom minst én forespørsel oppdateres") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)
            val expectedForespoerselId = UUID.randomUUID()

            every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 1
            every {
                mockForespoerselDao
                    .hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId))
                    .firstOrNull()
                    ?.forespoerselId
            } returns expectedForespoerselId

            mockStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns utesendingstidspunkt
                mockInnkommendeMelding(inntektsmeldingHaandtert)
            }

            verify {
                mockPriProducer.send(
                    inntektsmeldingHaandtert.vedtaksperiodeId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                    Pri.Key.SENDT_TID to utesendingstidspunkt.toJson(),
                )
            }
        }

        test("Sier _ikke_ ifra til Simba om besvart forespørsel dersom ingen forespørsler oppdateres") {
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

            every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 0

            mockInnkommendeMelding(inntektsmeldingHaandtert)

            verify(exactly = 0) {
                mockPriProducer.send(any<UUID>(), *anyVararg())
            }
        }

        context("VedtaksperiodeIder fra Spleis matcher ikke Bro - spleis sender IM_MOTTATT på en ukjent vedtaksperiodeID") {
            // Håndteringen og disse testene kan fjernes når vi ikke lengre tar imot IM fra altinn2
            val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)
            val forespoerselMedAnnenVedtaksperiodeId =
                mockForespoerselDto().copy(
                    vedtaksperiodeId = UUID.randomUUID(),
                    orgnr = inntektsmeldingHaandtert.orgnr,
                    fnr = inntektsmeldingHaandtert.fnr,
                )
            val forkastetForespoersel = forespoerselMedAnnenVedtaksperiodeId.copy(status = Status.FORKASTET)
            val forespoerselMedAnnetOrgnr =
                mockForespoerselDto().copy(
                    vedtaksperiodeId = UUID.randomUUID(),
                    orgnr = Orgnr.genererGyldig(),
                    fnr = inntektsmeldingHaandtert.fnr,
                )

            test("Lukker og sier ifra til Simba hvis det bare er en aktiv forespørsel") {

                every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 0

                every { mockForespoerselDao.hentForespoerslerForPerson(inntektsmeldingHaandtert.fnr) } returns
                    listOf(forespoerselMedAnnenVedtaksperiodeId)

                mockInnkommendeMelding(inntektsmeldingHaandtert)
                verify(exactly = 1) {
                    // forsøker først med meldingen as-is:
                    mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                        vedtaksperiodeId = inntektsmeldingHaandtert.vedtaksperiodeId,
                        besvart = any(),
                        inntektsmeldingId = any(),
                    )
                    // slår så opp på fnr
                    mockForespoerselDao.hentForespoerslerForPerson(inntektsmeldingHaandtert.fnr)
                    mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                        vedtaksperiodeId = forespoerselMedAnnenVedtaksperiodeId.vedtaksperiodeId,
                        besvart = any(),
                        inntektsmeldingId = any(),
                    ) // oppdaterer aktiv fsp
                    mockPriProducer.send(any<UUID>(), *anyVararg())
                }
            }

            test("Lukker og sier ifra til Simba hvis det bare er en aktiv forespørsel, ser bort fra andre orgnr og statuser") {

                every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 0

                every { mockForespoerselDao.hentForespoerslerForPerson(inntektsmeldingHaandtert.fnr) } returns
                    listOf(forespoerselMedAnnenVedtaksperiodeId, forkastetForespoersel, forespoerselMedAnnetOrgnr)

                mockInnkommendeMelding(inntektsmeldingHaandtert)
                verify(exactly = 1) {
                    // forsøker først med meldingen:
                    mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                        vedtaksperiodeId = inntektsmeldingHaandtert.vedtaksperiodeId,
                        besvart = any(),
                        inntektsmeldingId = any(),
                    )
                    // slår opp på fnr:
                    mockForespoerselDao.hentForespoerslerForPerson(inntektsmeldingHaandtert.fnr)
                    mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                        vedtaksperiodeId = forespoerselMedAnnenVedtaksperiodeId.vedtaksperiodeId,
                        besvart = any(),
                        inntektsmeldingId = any(),
                    ) // oppdaterer aktiv fsp
                    mockPriProducer.send(any<UUID>(), *anyVararg())
                }
            }

            test("Logger og gjør ingenting hvis flere enn en aktiv forespørsel") {

                every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 0

                every { mockForespoerselDao.hentForespoerslerForPerson(inntektsmeldingHaandtert.fnr) } returns
                    listOf(
                        forespoerselMedAnnenVedtaksperiodeId,
                        forespoerselMedAnnenVedtaksperiodeId.copy(forespoerselId = UUID.randomUUID()),
                    )

                mockInnkommendeMelding(inntektsmeldingHaandtert)
                verify(exactly = 1) {
                    mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                        inntektsmeldingHaandtert.vedtaksperiodeId,
                        any(),
                        any(),
                    ) // forsøker først med meldingen
                    mockForespoerselDao.hentForespoerslerForPerson(inntektsmeldingHaandtert.fnr) // slår opp på fnr
                }
                verify(exactly = 0) {
                    mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                        forespoerselMedAnnenVedtaksperiodeId.vedtaksperiodeId,
                        any(),
                        any(),
                    ) // oppdaterer aktiv fsp
                    mockPriProducer.send(any<UUID>(), *anyVararg())
                }
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
                    .firstOrNull()
                    ?.forespoerselId
            } returns expectedForespoerselId

            mockStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns utesendingstidspunkt
                mockInnkommendeMelding(inntektsmeldingHaandtert)
            }

            verify {
                mockPriProducer.send(
                    inntektsmeldingHaandtert.vedtaksperiodeId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                    Pri.Key.SENDT_TID to utesendingstidspunkt.toJson(),
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
                    .firstOrNull()
                    ?.forespoerselId
            } returns expectedForespoerselId

            mockStatic(LocalDateTime::class) {
                every { LocalDateTime.now() } returns utesendingstidspunkt
                mockInnkommendeMelding(inntektsmeldingHaandtert)
            }

            verify {
                mockPriProducer.send(
                    inntektsmeldingHaandtert.vedtaksperiodeId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                    Pri.Key.SENDT_TID to utesendingstidspunkt.toJson(),
                    Pri.Key.SPINN_INNTEKTSMELDING_ID to MockUuid.inntektsmeldingId.toJson(),
                )
            }
        }
    })
