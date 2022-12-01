package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.MOCK_UUID
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespurtDataListe
import java.util.UUID

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
            Key.TYPE to FORESPOERSEL_TYPE.tryToJson(),
            Key.FOM to forespoerselDto.fom.toString().tryToJson(),
            Key.TOM to forespoerselDto.tom.toString().tryToJson(),
            Key.ORGANISASJONSNUMMER to forespoerselDto.orgnr.tryToJson(),
            Key.FØDSELSNUMMER to forespoerselDto.fnr.tryToJson(),
            Key.VEDTAKSPERIODE_ID to MOCK_UUID.tryToJson(),
            Key.FORESPURT_DATA to mockForespurtDataListe().tryToJson()
        )

        verifySequence {
            mockForespoerselDao.lagre(
                withArg {
                    it.shouldBeEqualToIgnoringFields(forespoerselDto, forespoerselDto::oppdatert, forespoerselDto::opprettet)
                }
            )

            mockPriProducer.send(expectedForespoerselMottatt, any())
        }
    }
})
