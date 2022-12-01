package no.nav.helsearbeidsgiver.bro.sykepenger

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import org.slf4j.LoggerFactory
import java.util.UUID

const val EVENT_TYPE = "TRENGER_FORESPURT_DATA"

/* Tilgjengeliggjør hvilke data spleis forespør fra arbeidsgiver */
class ForespurtDataRiver(
    rapidsConnection: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer
) : River.PacketListener {
    private val logg = LoggerFactory.getLogger(this::class.java)
    private val sikkerlogg = sikkerLogger()

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue(Key.EVENT_TYPE.str, EVENT_TYPE)
                it.requireKey(
                    Key.ORGANISASJONSNUMMER.str,
                    Key.FØDSELSNUMMER.str,
                    Key.VEDTAKSPERIODE_ID.str
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logg.info("Mottok melding av type '${packet.value(Key.EVENT_TYPE).asText()}'")
        sikkerlogg.info("Mottok melding med innhold:\n${packet.toJson()}")

        val trengerForespurtData = TrengerForespurtData(
            fnr = packet.value(Key.FNR).asText(),
            orgnr = packet.value(Key.ORGNR).asText(),
            vedtaksperiodeId = packet.value(Key.VEDTAKSPERIODE_ID).asText().let(UUID::fromString)
        )
    }
}
