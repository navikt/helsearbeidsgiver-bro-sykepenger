package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.db.FunSpecWithDb
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.shouldBeEqualToWithApproximateDateTime
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.somOpprettet
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.tilMeldingForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.tilMeldingForespoerselOppdatert
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class LagreBegrensetForespoerselRiverTest :
    FunSpecWithDb({ db ->
        val testRapid = TestRapid()
        val forespoerselDao = spyk(ForespoerselDao(db))
        val mockPriProducer = mockk<PriProducer>(relaxed = true)

        LagreBegrensetForespoerselRiver(
            rapid = testRapid,
            forespoerselDao = forespoerselDao,
            priProducer = mockPriProducer,
        )

        fun mockInnkommendeMelding(forespoersel: ForespoerselDto) {
            testRapid.sendJson(
                Spleis.Key.TYPE to Spleis.Event.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_BEGRENSET.toJson(Spleis.Event.serializer()),
                Spleis.Key.ORGANISASJONSNUMMER to forespoersel.orgnr.toJson(),
                Spleis.Key.FØDSELSNUMMER to forespoersel.fnr.toJson(),
                Spleis.Key.VEDTAKSPERIODE_ID to forespoersel.vedtaksperiodeId.toJson(),
                Spleis.Key.SYKMELDINGSPERIODER to forespoersel.sykmeldingsperioder.toJson(Periode.serializer().list()),
                Spleis.Key.FORESPURT_DATA to forespoersel.forespurtData.toJson(SpleisForespurtDataDto.serializer().set()),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Forespørsel blir lagret og sender notifikasjon") {
            val forespoersel = mockBegrensetForespoerselDto()

            mockStatic(::randomUuid) {
                every { randomUuid() } returns forespoersel.forespoerselId

                mockStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns forespoersel.opprettet
                    mockInnkommendeMelding(forespoersel)
                }
            }

            verifySequence {
                forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(forespoersel.vedtaksperiodeId)
                forespoerselDao.hentForespoerslerForVedtaksperiodeIdListe(setOf(forespoersel.vedtaksperiodeId))
                forespoerselDao.lagre(
                    withArg {
                        it.shouldBeEqualToWithApproximateDateTime(forespoersel)
                    },
                    forespoersel.forespoerselId,
                )

                mockPriProducer.send(
                    forespoersel.vedtaksperiodeId,
                    *forespoersel.tilMeldingForespoerselMottatt(
                        skalHaPaaminnelse = false,
                    ),
                )
            }
        }

        test("Oppdatert forespørsel (ubesvart) blir lagret sender notifikasjon om oppdatering") {
            val eksponertForespoerselId = UUID.randomUUID()
            val nyForespoersel = mockBegrensetForespoerselDto()

            // Lagrer gammel forespoersel
            nyForespoersel
                .copy(
                    forespoerselId = eksponertForespoerselId,
                    forespurtData = setOf(SpleisInntekt), // Ikke duplikat
                ).somOpprettet(nyForespoersel.opprettet.minusDays(1))
                .also {
                    forespoerselDao.lagre(it, it.forespoerselId)
                }

            // Fjerner kall fra klargjøring av tilstand
            clearMocks(forespoerselDao)

            mockkStatic(::randomUuid) {
                every { randomUuid() } returns nyForespoersel.forespoerselId

                mockStatic(LocalDateTime::class) {
                    every { LocalDateTime.now() } returns nyForespoersel.opprettet
                    mockInnkommendeMelding(nyForespoersel)
                }
            }

            verifySequence {
                forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(nyForespoersel.vedtaksperiodeId)
                forespoerselDao.hentForespoerslerForVedtaksperiodeIdListe(setOf(nyForespoersel.vedtaksperiodeId))
                forespoerselDao.lagre(
                    withArg {
                        it.shouldBeEqualToWithApproximateDateTime(nyForespoersel)
                    },
                    eksponertForespoerselId,
                )
            }

            verifySequence {
                mockPriProducer.send(
                    nyForespoersel.vedtaksperiodeId,
                    *nyForespoersel.tilMeldingForespoerselOppdatert(eksponertForespoerselId),
                )
            }
        }

        test(
            "Duplisert forespørsel blir hverken lagret eller sender notifikasjon, men oppdaterer tidspunkt for forrige kontakt fra Spleis",
        ) {
            val eksponertForespoersel = mockBegrensetForespoerselDto().somOpprettet(LocalDateTime.now().minusDays(5))
            val aktivForespoersel =
                eksponertForespoersel
                    .copy(
                        forespoerselId = UUID.randomUUID(),
                        forespurtData = setOf(SpleisInntekt), // Ikke duplikat
                    ).somOpprettet(eksponertForespoersel.opprettet.plusDays(3))

            forespoerselDao.lagre(eksponertForespoersel, eksponertForespoersel.forespoerselId)
            forespoerselDao.lagre(aktivForespoersel, eksponertForespoersel.forespoerselId)

            // Fjerner kall fra klargjøring av tilstand
            clearMocks(forespoerselDao)

            mockkStatic(::randomUuid) {
                every { randomUuid() } returns aktivForespoersel.forespoerselId

                mockInnkommendeMelding(aktivForespoersel)
            }

            verifySequence {
                forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(aktivForespoersel.vedtaksperiodeId)
                forespoerselDao.hentForespoerslerForVedtaksperiodeIdListe(setOf(aktivForespoersel.vedtaksperiodeId))
                forespoerselDao.oppdaterForrigeKontaktFraSpleis(eksponertForespoersel.forespoerselId)
            }

            verify(exactly = 0) {
                forespoerselDao.lagre(any(), any())
                mockPriProducer.send(any<UUID>(), *anyVararg())
            }

            val lagredeForespoersler = forespoerselDao.hentForespoerslerForVedtaksperiodeIdListe(setOf(aktivForespoersel.vedtaksperiodeId))

            // Ingen endring
            lagredeForespoersler[0].also { (eksponertId, forespoersel) ->
                eksponertId shouldBe eksponertForespoersel.forespoerselId
                forespoersel.status shouldBe Status.FORKASTET
                forespoersel.forespoerselId shouldBe eksponertForespoersel.forespoerselId
                forespoersel.forrigeKontaktFraSpleis shouldBe eksponertForespoersel.opprettet
            }

            // 'forrigeKontaktFraSpleis' oppdatert
            lagredeForespoersler[1].also { (eksponertId, forespoersel) ->
                eksponertId shouldBe eksponertForespoersel.forespoerselId
                forespoersel.status shouldBe Status.AKTIV
                forespoersel.forespoerselId shouldBe aktivForespoersel.forespoerselId
                forespoersel.forrigeKontaktFraSpleis shouldBeAfter aktivForespoersel.opprettet
                forespoersel.forrigeKontaktFraSpleis.shouldBeWithin(1.seconds.toJavaDuration(), LocalDateTime.now())
            }
        }
    })

private fun mockBegrensetForespoerselDto(): ForespoerselDto =
    mockForespoerselDto().copy(
        type = Type.BEGRENSET,
        egenmeldingsperioder = emptyList(),
        bestemmendeFravaersdager = emptyMap(),
    )
