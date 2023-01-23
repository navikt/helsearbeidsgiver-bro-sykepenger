package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespurtDataListe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.sendJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson

class LagreForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    LagreForespoerselRiver(
        rapid = testRapid,
        forespoerselDao = mockForespoerselDao,
        priProducer = mockPriProducer
    )

    test("Innkommende forespørsler blir lagret og sender notifikasjon videre") {
        val forespoersel = mockForespoerselDto()

        val expectedPublished = ForespoerselMottatt(
            forespoerselId = forespoersel.forespoerselId,
            orgnr = forespoersel.orgnr,
            fnr = forespoersel.fnr
        )

        testRapid.sendJson(
            Key.TYPE to SpleisEvent.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER.toJson(),
            Key.ORGANISASJONSNUMMER to forespoersel.orgnr.toJson(),
            Key.FØDSELSNUMMER to forespoersel.fnr.toJson(),
            Key.VEDTAKSPERIODE_ID to forespoersel.vedtaksperiodeId.toJson(),
            Key.SYKMELDINGSPERIODER to forespoersel.sykmeldingsperioder.toJson(Json::encodeToJsonElement),
            Key.FORESPURT_DATA to mockForespurtDataListe().toJson(Json::encodeToJsonElement)
        )

        verifySequence {
            mockForespoerselDao.lagre(
                withArg {
                    it.shouldBeEqualToIgnoringFields(forespoersel, forespoersel::forespoerselId, forespoersel::oppdatert, forespoersel::opprettet)
                }
            )

            mockPriProducer.send(
                withArg {
                    it.shouldBeEqualToIgnoringFields(expectedPublished, expectedPublished::forespoerselId)
                },
                ForespoerselMottatt::toJson
            )
        }
    }
})
