package no.nav.helsearbeidsgiver.bro.sykepenger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.LoggerFactory

internal class ForespoerselRiver(
    rapidsConnection: RapidsConnection
) : River.PacketListener {
    val logg = LoggerFactory.getLogger(this::class.java)
    val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    val meldingstype = "TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER"
    val eventSvar = "opplysninger_fra_arbeidsgiver"

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("type", meldingstype)
                it.require(
                    "@opprettet" to JsonNode::asLocalDateTime,
                    "fom" to JsonNode::asLocalDate,
                    "tom" to JsonNode::asLocalDate
                )
                it.requireKey("organisasjonsnummer", "fødselsnummer")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("mottok meldingstype: ${packet["type"].asText()}")
        sikkerlogg.info("mottok melding:\n${packet.toJson()}")

        packet["type"] = ""
        packet["@event_name"] = eventSvar

        packet["arbeidsgiveropplysninger"] = PriDto(
            "dette er en periode",
            "dette er en refusjon",
            "dette er en inntekt"
        )

        context.publish(packet.toJson())

        "Publiserte '$eventSvar' til sparkel-arbeidsgiver".let {
            logg.info(it)
            sikkerlogg.info("$it med data=${packet.toJson()}")
        }
    }
}

class PriDto(
    val periode: String?,
    val refusjon: String?,
    val inntekt: String?
)

private fun JsonMessage.require(vararg keyParserPairs: Pair<String, (JsonNode) -> Any>) {
    keyParserPairs.forEach { (key, parser) ->
        this.require(key, parser)
    }
}
