package no.nav.helsearbeidsgiver.bro.sykepenger

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.januar
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespurtDataListe
import java.util.*

const val MOCK_UUID = "01234567-abcd-0123-abcd-012345678901"

class ForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()
    val forespoerselDao = mockk<ForespoerselDao>(relaxed = true)

    ForespoerselRiver(
        rapidsConnection = testRapid,
        forespoerselDao = forespoerselDao
    )

    val sikkerlogCollector = ListAppender<ILoggingEvent>().also {
        (sikkerLogger() as Logger).addAppender(it)
        it.start()
    }

    test("ForespørselRiver plukker opp og logger events") {

        val forespoerselDto = ForespoerselDto(
            orgnr = "12345678901",
            fnr = "123456789",
            vedtaksperiodeId = UUID.fromString(MOCK_UUID),
            fom = 1.januar,
            tom = 16.januar,
            forespurtData = mockForespurtDataListe(),
            forespoerselBesvart = null,
            status = Status.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER
        )
        val eventMap: Map<Key, JsonElement> = mapOf(
            Key.TYPE to "TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER".toJson(),
            Key.FOM to forespoerselDto.fom.toString().toJson(),
            Key.TOM to forespoerselDto.tom.toString().toJson(),
            Key.ORGANISASJONSNUMMER to forespoerselDto.orgnr.toJson(),
            Key.FØDSELSNUMMER to forespoerselDto.fnr.toJson(),
            Key.VEDTAKSPERIODE_ID to MOCK_UUID.toJson(),
            Key.FORESPURT_DATA to mockForespurtDataListe().toJson()
        )

        val event = eventMap.mapKeys { (key, _) -> key.str }
            .toJson()
            .toString()

        withConstantNow(forespoerselDto.oppdatert) {
            testRapid.sendTestMessage(event)
        }
        sikkerlogCollector.list.size shouldBeExactly 2
        sikkerlogCollector.list.single { it.message.contains("mottok melding:") }
        sikkerlogCollector.list.single { it.message.contains("forespoersel:") }
        verify(exactly = 1) { forespoerselDao.lagre(forespoerselDto) }
    }
})

/** Obs! Denne kan feile runtime. */
private inline fun <reified T : Any> T.toJson(): JsonElement =
    Json.encodeToJsonElement(this)
