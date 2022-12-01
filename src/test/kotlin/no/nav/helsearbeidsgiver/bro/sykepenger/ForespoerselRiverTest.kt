package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.MOCK_UUID
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespurtDataListe
import java.util.*

class ForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselDao = mockk<ForespoerselDao>(relaxed = true)
    val mockPriProducer = mockk<PriProducer>(relaxed = true)

    ForespoerselRiver(
        rapidsConnection = testRapid,
        forespoerselDao = mockForespoerselDao,
        priProducer = mockPriProducer
    )

    test("Innkommende forespørsler blir lagret og sender notifikasjon videre") {
        val forespoerselDto = mockForespoerselDto()

        val expectedForespoerselMottatt = ForespoerselMottatt(
            orgnr = forespoerselDto.orgnr,
            fnr = forespoerselDto.fnr,
            vedtaksperiodeId = UUID.fromString(MOCK_UUID)
        )

        testRapid.sendJson(
            Key.TYPE to FORESPOERSEL_TYPE.toJson(),
            Key.FOM to forespoerselDto.fom.toString().toJson(),
            Key.TOM to forespoerselDto.tom.toString().toJson(),
            Key.ORGANISASJONSNUMMER to forespoerselDto.orgnr.toJson(),
            Key.FØDSELSNUMMER to forespoerselDto.fnr.toJson(),
            Key.VEDTAKSPERIODE_ID to MOCK_UUID.toJson(),
            Key.FORESPURT_DATA to mockForespurtDataListe().toJson()
        )

        verifySequence {
            mockForespoerselDao.lagre(
                withArg {
                    it.shouldBeEqualToIgnoringFields(forespoerselDto, forespoerselDto::oppdatert, forespoerselDto::opprettet)
                }
            )

            mockPriProducer.send(expectedForespoerselMottatt)
        }
    }
})

private fun TestRapid.sendJson(vararg keyValuePairs: Pair<Key, JsonElement>) {
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .toJson()
        .toString()
        .let(this::sendTestMessage)
}

/** Obs! Denne kan feile runtime. */
private inline fun <reified T : Any> T.toJson(): JsonElement =
    Json.encodeToJsonElement(this)
