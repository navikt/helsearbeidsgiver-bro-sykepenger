package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.Env.fromEnv
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockInntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.toJson

class MarkerBesvartForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    MarkerBesvartForespoerselRiver(
        rapid = testRapid,
        forespoerselDao = mockForespoerselDao,
        priProducer = mockPriProducer
    )

    fun mockInnkommendeMelding(inntektsmeldingHaandtert: InntektsmeldingHaandtertDto) {
        testRapid.sendJson(
            Spleis.Key.TYPE to Spleis.Event.INNTEKTSMELDING_HÅNDTERT.toJson(Spleis.Event.serializer()),
            Spleis.Key.ORGANISASJONSNUMMER to inntektsmeldingHaandtert.orgnr.toJson(Orgnr.serializer()),
            Spleis.Key.FØDSELSNUMMER to inntektsmeldingHaandtert.fnr.toJson(String.serializer()),
            Spleis.Key.VEDTAKSPERIODE_ID to inntektsmeldingHaandtert.vedtaksperiodeId.toJson(),
            Spleis.Key.DOKUMENT_ID to inntektsmeldingHaandtert.inntektsmeldingId?.toJson(),
            Spleis.Key.OPPRETTET to inntektsmeldingHaandtert.haandtert.toJson()
        )
    }

    beforeEach {
        clearAllMocks()
    }

    test("Innkommende event oppdaterer aktive forespørsler som er besvart") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = MockUuid.inntektsmeldingId)

        mockkObject(Env) {
            every { Env.VarName.PILOT_TILLATTE_ORGANISASJONER.fromEnv() } returns inntektsmeldingHaandtert.orgnr.verdi
            mockInnkommendeMelding(inntektsmeldingHaandtert)
        }

        verifySequence {
            mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)
            mockForespoerselDao.oppdaterForespoerslerSomBesvart(inntektsmeldingHaandtert.vedtaksperiodeId, inntektsmeldingHaandtert.haandtert, inntektsmeldingHaandtert.inntektsmeldingId)
            mockForespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(inntektsmeldingHaandtert.vedtaksperiodeId)
        }
    }

    test("Tåler at dokumentId mangler på innkommende event") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

        mockkObject(Env) {
            every { Env.VarName.PILOT_TILLATTE_ORGANISASJONER.fromEnv() } returns inntektsmeldingHaandtert.orgnr.verdi
            mockInnkommendeMelding(inntektsmeldingHaandtert)
        }

        verifySequence {
            mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)
            mockForespoerselDao.oppdaterForespoerslerSomBesvart(inntektsmeldingHaandtert.vedtaksperiodeId, inntektsmeldingHaandtert.haandtert, inntektsmeldingHaandtert.inntektsmeldingId)
            mockForespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(inntektsmeldingHaandtert.vedtaksperiodeId)
        }
    }

    test("Sier ifra til Simba om besvart forespørsel") {
        val forespoersel = mockForespoerselDto()
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

        every {
            mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)
        } returns forespoersel
        every {
            mockForespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(inntektsmeldingHaandtert.vedtaksperiodeId)
        } returns forespoersel.forespoerselId

        mockkObject(Env) {
            every { Env.VarName.PILOT_TILLATTE_ORGANISASJONER.fromEnv() } returns inntektsmeldingHaandtert.orgnr.verdi
            mockInnkommendeMelding(inntektsmeldingHaandtert)
        }

        verifySequence {
            mockPriProducer.send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson()
            )
        }
    }

    test("Sier _ikke_ ifra til Simba om besvart forespørsel dersom forespørselen allerede er besvart") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

        every {
            mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)
        } returns null

        mockkObject(Env) {
            every { Env.VarName.PILOT_TILLATTE_ORGANISASJONER.fromEnv() } returns inntektsmeldingHaandtert.orgnr.verdi
            mockInnkommendeMelding(inntektsmeldingHaandtert)
        }

        verify(exactly = 0) {
            mockPriProducer.send(*anyVararg())
        }
    }

    test("Sender forespørselId-en portalen forventer når forespørsel markeres som besvart") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)
        val expectedForespoerselId = randomUuid()
        every {
            mockForespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)
        } returns mockForespoerselDto()
        every {
            mockForespoerselDao.forespoerselIdKnyttetTilOppgaveIPortalen(inntektsmeldingHaandtert.vedtaksperiodeId)
        } returns expectedForespoerselId

        mockkObject(Env) {
            every { Env.VarName.PILOT_TILLATTE_ORGANISASJONER.fromEnv() } returns inntektsmeldingHaandtert.orgnr.verdi
            mockInnkommendeMelding(inntektsmeldingHaandtert)
        }

        verify {
            mockPriProducer.send(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to expectedForespoerselId.toJson()
            )
        }
    }
})
