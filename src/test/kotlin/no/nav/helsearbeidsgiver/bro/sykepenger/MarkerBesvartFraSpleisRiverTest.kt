package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockInntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson

class MarkerBesvartFraSpleisRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    MarkerBesvartFraSpleisRiver(
        rapid = testRapid,
        forespoerselDao = mockForespoerselDao,
        priProducer = mockPriProducer,
    )

    fun mockInnkommendeMelding(inntektsmeldingHaandtert: InntektsmeldingHaandtertDto) {
        testRapid.sendJson(
            Spleis.Key.TYPE to Spleis.Event.INNTEKTSMELDING_HÅNDTERT.toJson(Spleis.Event.serializer()),
            Spleis.Key.ORGANISASJONSNUMMER to inntektsmeldingHaandtert.orgnr.toJson(Orgnr.serializer()),
            Spleis.Key.FØDSELSNUMMER to inntektsmeldingHaandtert.fnr.toJson(String.serializer()),
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
            mockForespoerselDao.forespoerselIdEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeId)
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
            mockForespoerselDao.forespoerselIdEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeId)
        }
    }

    test("Sier ifra til Simba om besvart forespørsel dersom minst én forespørsel oppdateres") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)
        val expectedForespoerselId = randomUuid()

        every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 1

        every {
            mockForespoerselDao.forespoerselIdEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeId)
        } returns expectedForespoerselId

        mockInnkommendeMelding(inntektsmeldingHaandtert)

        verifySequence {
            mockPriProducer.send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
            )
        }
    }

    test("Sier _ikke_ ifra til Simba om besvart forespørsel dersom ingen forespørsler oppdateres") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

        every { mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(any(), any(), any()) } returns 0

        mockInnkommendeMelding(inntektsmeldingHaandtert)

        verify(exactly = 0) {
            mockPriProducer.send(*anyVararg())
        }
    }

    test("Sender forespørselId-en Simba forventer når forespørsel markeres som besvart") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)
        val expectedForespoerselId = randomUuid()

        every {
            mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                inntektsmeldingHaandtert.vedtaksperiodeId,
                any(),
                any(),
            )
        } returns 1

        every {
            mockForespoerselDao.forespoerselIdEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeId)
        } returns expectedForespoerselId

        mockInnkommendeMelding(inntektsmeldingHaandtert)

        verify {
            mockPriProducer.send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
            )
        }
    }

    test("Videresender inntektsmeldingId når forespørsel markeres som besvart") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = MockUuid.inntektsmeldingId)
        val expectedForespoerselId = randomUuid()

        every {
            mockForespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                inntektsmeldingHaandtert.vedtaksperiodeId,
                any(),
                any(),
            )
        } returns 1

        every {
            mockForespoerselDao.forespoerselIdEksponertTilSimba(inntektsmeldingHaandtert.vedtaksperiodeId)
        } returns expectedForespoerselId

        mockInnkommendeMelding(inntektsmeldingHaandtert)

        verify {
            mockPriProducer.send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
                Pri.Key.SPINN_INNTEKTSMELDING_ID to MockUuid.inntektsmeldingId.toJson(),
            )
        }
    }
})
