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
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sikkerlogger = sikkerLogger()

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
        logger.info("Mottok melding av type '${packet.value(Key.EVENT_TYPE).asText()}'")
        sikkerlogger.info("Mottok melding med innhold:\n${packet.toJson()}")

        val trengerForespurtData = TrengerForespurtData(
            fnr = packet.value(Key.FNR).asText(),
            orgnr = packet.value(Key.ORGNR).asText(),
            vedtaksperiodeId = packet.value(Key.VEDTAKSPERIODE_ID).asText().let(UUID::fromString)
        )

        val forespoersel = forespoerselDao.hentAktivForespørselFor(trengerForespurtData.vedtaksperiodeId)
    }
}
