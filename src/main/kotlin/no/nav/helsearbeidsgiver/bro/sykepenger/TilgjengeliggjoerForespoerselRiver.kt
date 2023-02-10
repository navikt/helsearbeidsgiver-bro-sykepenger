package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.value
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.asUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.sikkerLogger
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson
import org.slf4j.LoggerFactory

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
                it.demandValue(Pri.Key.BEHOV.str, ForespoerselSvar.behovType.name)
                it.rejectKey(Pri.Key.LØSNING.str)
                it.requireKey(
                    Pri.Key.FORESPOERSEL_ID.str,
                    Pri.Key.BOOMERANG.str
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding på pri-topic av type '${packet.value(Pri.Key.BEHOV).asText()}'.")
        sikkerlogger.info("Mottok melding på pri-topic med innhold:\n${packet.toJson()}")

        val forespoerselId = packet.value(Pri.Key.FORESPOERSEL_ID).asUuid()
        val boomerang = packet.value(Pri.Key.BOOMERANG).toString().let { Json.decodeFromString<Map<String, JsonElement>>(it) }

        val forespoersel = forespoerselDao.hentAktivForespoerselFor(forespoerselId)

        val forespoerselSvar = if (forespoersel != null) {
            ForespoerselSvar(
                forespoerselId = forespoerselId,
                resultat = ForespoerselSvar.Suksess(forespoersel),
                boomerang = boomerang
            )
        } else {
            ForespoerselSvar(
                forespoerselId = forespoerselId,
                feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET,
                boomerang = boomerang
            )
        }

        priProducer.send(
            Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(),
            Pri.Key.LØSNING to forespoerselSvar.let(Json::encodeToJsonElement)
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        "Innkommende melding har feil.".let {
            logger.info("$it Se sikker logg for mer info.")
            sikkerlogger.info("$it Detaljer:\n${problems.toExtendedReport()}")
        }
    }
}
