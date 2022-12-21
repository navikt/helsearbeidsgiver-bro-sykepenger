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
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.asUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.ifFalse
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.ifTrue
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.require
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.sikkerLogger
import org.slf4j.LoggerFactory

class LagreForespoerselRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer
) : River.PacketListener {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sikkerlogger = sikkerLogger()

    init {
        River(rapid).apply {
            validate {
                it.demandValue(Key.TYPE.str, SpleisEvent.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER.name)
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
        logger.info("Mottok melding av type '${packet.value(Key.TYPE).asText()}'")
        sikkerlogger.info("Mottok melding med innhold:\n${packet.toJson()}")

        val forespoersel = ForespoerselDto(
            orgnr = packet.value(Key.ORGANISASJONSNUMMER).asText(),
            fnr = packet.value(Key.FØDSELSNUMMER).asText(),
            vedtaksperiodeId = packet.value(Key.VEDTAKSPERIODE_ID).asUuid(),
            fom = packet.value(Key.FOM).asLocalDate(),
            tom = packet.value(Key.TOM).asLocalDate(),
            forespurtData = packet.value(Key.FORESPURT_DATA).toString().let(Json::decodeFromString),
            forespoerselBesvart = null,
            status = Status.AKTIV
        )

        sikkerlogger.info("Forespoersel lest: $forespoersel")

        forespoerselDao.lagre(forespoersel)
            .let { id ->
                if (id != null) {
                    logger.info("Forespørsel lagret med id=$id.")
                } else {
                    logger.info("Forespørsel ble ikke lagret.")
                }
            }

        priProducer.send(
            ForespoerselMottatt(
                orgnr = forespoersel.orgnr,
                fnr = forespoersel.fnr,
                vedtaksperiodeId = forespoersel.vedtaksperiodeId
            ),
            ForespoerselMottatt::toJson
        )
            .ifTrue { logger.info("Sa ifra om mottatt forespørsel til Simba.") }
            .ifFalse { logger.info("Klarte ikke si ifra om mottatt forespørsel til Simba.") }
    }
}
