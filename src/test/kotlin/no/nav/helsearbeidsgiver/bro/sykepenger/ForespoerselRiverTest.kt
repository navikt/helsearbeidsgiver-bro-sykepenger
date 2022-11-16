package no.nav.helsearbeidsgiver.bro.sykepenger

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.februar
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mars
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
        val event = mapOf(
            Key.TYPE to "TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER",
            Key.FOM to 1.februar,
            Key.TOM to 1.mars,
            Key.ORGANISASJONSNUMMER to "123456789",
            Key.FØDSELSNUMMER to "12345678901",
            Key.VEDTAKSPERIODE_ID to MOCK_UUID,
            Key.FORESPURT_DATA to mockForespurtDataListe().let(Json::encodeToString)
        )
            .mapKeys { (key, _) -> key.str }
            .let(JsonMessage::newMessage)
            .toJson()

        testRapid.sendTestMessage(event)

        sikkerlogCollector.list.size shouldBeExactly 2
        sikkerlogCollector.list.single { it.message.contains("mottok melding:") }
        sikkerlogCollector.list.single { it.message.contains("forespoersel:") }
    }
})
