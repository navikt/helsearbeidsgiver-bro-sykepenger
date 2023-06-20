package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.Env.fromEnv
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockInntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson

class MarkerHaandertForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)

    MarkerHaandertForespoerselRiver(
        rapid = testRapid,
        forespoerselDao = mockForespoerselDao
    )

    fun mockInnkommendeMelding(inntektsmeldingHaandtert: InntektsmeldingHaandtertDto) {
        testRapid.sendJson(
            Spleis.Key.TYPE to Spleis.Event.INNTEKTSMELDING_HÅNDTERT.toJson(Spleis.Event.serializer()),
            Spleis.Key.ORGANISASJONSNUMMER to inntektsmeldingHaandtert.orgnr.toJson(Orgnr.serializer()),
            Spleis.Key.VEDTAKSPERIODE_ID to inntektsmeldingHaandtert.vedtaksperiodeId.toJson()
        )
    }

    beforeEach {
        clearAllMocks()
    }

    test("Innkommende event oppdaterer status til BESVART for aktive forespørsler") {
        val inntektsmeldingHaandtert = mockInntektsmeldingHaandtertDto()

        mockkObject(Env) {
            every { Env.VarName.PILOT_TILLATTE_ORGANISASJONER.fromEnv() } returns inntektsmeldingHaandtert.orgnr.verdi
            mockInnkommendeMelding(inntektsmeldingHaandtert)
        }

        val expectedPublished = InntektsmeldingHaandtertDto(
            orgnr = inntektsmeldingHaandtert.orgnr,
            vedtaksperiodeId = inntektsmeldingHaandtert.vedtaksperiodeId
        )
        verifySequence {
            mockForespoerselDao.oppdaterStatusForAktiveForespoersler(expectedPublished.vedtaksperiodeId, Status.BESVART)
        }
    }
})
