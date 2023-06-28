package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.Env.fromEnv
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.MockUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockInntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.time.LocalDateTime

class MarkerBesvartForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)

    MarkerBesvartForespoerselRiver(
        rapid = testRapid,
        forespoerselDao = mockForespoerselDao
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

        val expectedPublished = InntektsmeldingHaandtertDto(
            orgnr = inntektsmeldingHaandtert.orgnr,
            fnr = inntektsmeldingHaandtert.fnr,
            vedtaksperiodeId = inntektsmeldingHaandtert.vedtaksperiodeId,
            inntektsmeldingId = inntektsmeldingHaandtert.inntektsmeldingId,
            haandtert = LocalDateTime.MAX
        )
        verifySequence {
            mockForespoerselDao.oppdaterForespoerslerSomBesvart(expectedPublished.vedtaksperiodeId, Status.BESVART, expectedPublished.haandtert, expectedPublished.inntektsmeldingId)
        }
    }

    test("Tåler at dokumentId mangler på innkommende event") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto(dokumentId = null)

        mockkObject(Env) {
            every { Env.VarName.PILOT_TILLATTE_ORGANISASJONER.fromEnv() } returns inntektsmeldingHaandtert.orgnr.verdi
            mockInnkommendeMelding(inntektsmeldingHaandtert)
        }

        val expectedPublished = InntektsmeldingHaandtertDto(
            orgnr = inntektsmeldingHaandtert.orgnr,
            fnr = inntektsmeldingHaandtert.fnr,
            vedtaksperiodeId = inntektsmeldingHaandtert.vedtaksperiodeId,
            inntektsmeldingId = inntektsmeldingHaandtert.inntektsmeldingId,
            haandtert = LocalDateTime.MAX
        )
        verifySequence {
            mockForespoerselDao.oppdaterForespoerslerSomBesvart(expectedPublished.vedtaksperiodeId, Status.BESVART, LocalDateTime.MAX, expectedPublished.inntektsmeldingId)
        }
    }

    test("Skal ikke sende ut event om å slette oppgave for forespørsler som allerede er besvart") {
        // TODO
    }
})
