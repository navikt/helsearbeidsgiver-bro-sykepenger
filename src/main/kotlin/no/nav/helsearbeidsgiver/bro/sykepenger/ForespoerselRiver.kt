package no.nav.helsearbeidsgiver.bro.sykepenger

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import org.slf4j.LoggerFactory
import java.util.UUID

class ForespoerselRiver(
    rapidsConnection: RapidsConnection,
    val forespoerselDao: ForespoerselDao
) : River.PacketListener {
    private val logg = LoggerFactory.getLogger(this::class.java)
    private val sikkerlogg = sikkerLogger()

    val meldingstype = "TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER"

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.TYPE.str, meldingstype)
                it.require(
                    Key.FOM.str to JsonNode::asLocalDate,
                    Key.TOM.str to JsonNode::asLocalDate
                )
                it.requireKey(
                    Key.ORGANISASJONSNUMMER.str,
                    Key.FØDSELSNUMMER.str,
                    Key.VEDTAKSPERIODE_ID.str,
                    Key.FORESPURT_DATA.str
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("mottok meldingstype: ${packet.value(Key.TYPE).asText()}")
        sikkerlogg.info("mottok melding:\n${packet.toJson()}")

        val forespoersel = ForespoerselDto(
            orgnr = packet.value(Key.ORGANISASJONSNUMMER).asText(),
            fnr = packet.value(Key.FØDSELSNUMMER).asText(),
            vedtaksperiodeId = packet.value(Key.VEDTAKSPERIODE_ID).asText().let(UUID::fromString),
            fom = packet.value(Key.FOM).asLocalDate(),
            tom = packet.value(Key.TOM).asLocalDate(),
            // TODO ikke helt korrekt måte å deserialisere
            forespurtData = packet.value(Key.FORESPURT_DATA).toString().let(Json::decodeFromString),
            forespoerselBesvart = null,
            status = Status.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER
        )
        sikkerlogg.info("forespoersel: $forespoersel")
        forespoerselDao.lagre(forespoersel)
    }
}

private fun JsonMessage.require(vararg keyParserPairs: Pair<String, (JsonNode) -> Any>) {
    keyParserPairs.forEach { (key, parser) ->
        this.require(key, parser)
    }
}
