package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.TrengerForespoersel
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.value
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.sikkerLogger
import org.slf4j.LoggerFactory
import java.util.UUID

/* Tilgjengeliggjør hvilke data spleis forespør fra arbeidsgiver */
class TilgjengeliggjoerForespoerselRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer
) : River.PacketListener {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sikkerlogger = sikkerLogger()

    init {
        River(rapid).apply {
            validate {
                it.demandValue(Pri.Key.BEHOV.str, Pri.BehovType.TRENGER_FORESPØRSEL.name)
                it.requireKey(
                    Pri.Key.ORGNR.str,
                    Pri.Key.FNR.str,
                    Pri.Key.VEDTAKSPERIODE_ID.str
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding på pri-topic av type '${packet.value(Pri.Key.BEHOV).asText()}'.")
        sikkerlogger.info("Mottok melding på pri-topic med innhold:\n${packet.toJson()}")

        val trengerForespoersel = TrengerForespoersel(
            orgnr = packet.value(Pri.Key.ORGNR).asText(),
            fnr = packet.value(Pri.Key.FNR).asText(),
            vedtaksperiodeId = packet.value(Pri.Key.VEDTAKSPERIODE_ID).asText().let(UUID::fromString)
        )

        val forespoersel = forespoerselDao.hentAktivForespoerselFor(trengerForespoersel.vedtaksperiodeId)

        if (forespoersel != null) {
            priProducer.send(ForespoerselSvar(forespoersel), ForespoerselSvar::toJson)
        } else {
            priProducer.send("null", Json::encodeToJsonElement)
        }
    }
}
