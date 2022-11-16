package no.nav.helsearbeidsgiver.bro.sykepenger

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespurtDataListe

const val MOCK_UUID = "01234567-abcd-0123-abcd-012345678901"

class ForespoerselRiverTest : FunSpec({
    val testRapid = TestRapid()

    ForespoerselRiver(
        rapidsConnection = testRapid,
        forespoerselDao = mockk()
    )

    val sikkerlogCollector = ListAppender<ILoggingEvent>().also {
        (sikkerLogger() as Logger).addAppender(it)
        it.start()
    }

    test("ForespørselRiver plukker opp og logger events") {
        val eventMap: Map<Key, JsonElement> = mapOf(
            Key.TYPE to "TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER".toJson(),
            Key.FOM to "2018-01-01".toJson(),
            Key.TOM to "2018-01-16".toJson(),
            Key.ORGANISASJONSNUMMER to "123456789".toJson(),
            Key.FØDSELSNUMMER to "12345678901".toJson(),
            Key.VEDTAKSPERIODE_ID to MOCK_UUID.toJson(),
            Key.FORESPURT_DATA to mockForespurtDataListe().toJson()
        )

        val event = eventMap.mapKeys { (key, _) -> key.str }
            .toJson()
            .toString()

        testRapid.sendTestMessage(event)

        sikkerlogCollector.list.size shouldBeExactly 2
        sikkerlogCollector.list.single { it.message.contains("mottok melding:") }
        sikkerlogCollector.list.single { it.message.contains("forespoersel:") }
    }
})

/** Obs! Denne kan feile runtime. */
private inline fun <reified T : Any> T.toJson(): JsonElement =
    Json.encodeToJsonElement(this)
