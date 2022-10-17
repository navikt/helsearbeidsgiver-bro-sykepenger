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
    private val rapidsConnection: RapidsConnection
) : River.PacketListener {
    private companion object {
        val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        val logg = LoggerFactory.getLogger(this::class.java)
        const val meldingstype = "TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER"
    }
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("type", meldingstype) }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
            validate { it.require("fom", JsonNode::asLocalDate) }
            validate { it.require("tom", JsonNode::asLocalDate) }
            validate { it.requireKey("organisasjonsnummer", "fødselsnummer") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("mottok meldingstype: ${packet["type"].asText()}")
        sikkerlogg.info("mottok melding:\n${packet.toJson()}")
    }
}
