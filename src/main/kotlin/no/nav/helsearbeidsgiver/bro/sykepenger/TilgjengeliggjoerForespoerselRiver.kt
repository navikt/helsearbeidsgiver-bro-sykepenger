package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.UuidSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.fromJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.rejectKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
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
                it.demandValues(Pri.Key.BEHOV to ForespoerselSvar.behovType.name)
                it.rejectKeys(Pri.Key.LØSNING)
                it.requireKeys(
                    Pri.Key.FORESPOERSEL_ID,
                    Pri.Key.BOOMERANG
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding på pri-topic av type '${Pri.Key.BEHOV.fra(packet).fromJson(String.serializer())}'.")
        sikkerlogger.info("Mottok melding på pri-topic med innhold:\n${packet.toJson()}")

        val forespoerselSvar = ForespoerselSvar(
            forespoerselId = Pri.Key.FORESPOERSEL_ID.fra(packet).fromJson(UuidSerializer),
            boomerang = Pri.Key.BOOMERANG.fra(packet)
        )
            .let {
                val forespoersel = forespoerselDao.hentAktivForespoerselFor(it.forespoerselId)
                if (forespoersel != null) {
                    it.copy(resultat = ForespoerselSvar.Suksess(forespoersel))
                } else {
                    it.copy(feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET)
                }
            }

        priProducer.send(
            Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to forespoerselSvar.toJson(ForespoerselSvar.serializer())
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        "Innkommende melding har feil.".let {
            logger.info("$it Se sikker logg for mer info.")
            sikkerlogger.info("$it Detaljer:\n${problems.toExtendedReport()}")
        }
    }
}
