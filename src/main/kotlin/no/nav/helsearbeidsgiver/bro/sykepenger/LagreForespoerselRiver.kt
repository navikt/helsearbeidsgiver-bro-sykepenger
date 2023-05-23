package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue
import org.slf4j.Logger

sealed class LagreForespoerselRiver(
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer
) : River.PacketListener {
    abstract val logger: Logger
    private val sikkerlogger = sikkerLogger()

    abstract fun lesForespoersel(packet: JsonMessage): ForespoerselDto

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding av type '${Spleis.Key.TYPE.fra(packet).fromJson(String.serializer())}'")
        sikkerlogger.info("Mottok melding med innhold:\n${packet.toJson()}")

        val forespoersel = lesForespoersel(packet)
        sikkerlogger.info("Forespoersel lest: $forespoersel")

        if (forespoersel.orgnr in Env.AllowList.organisasjoner) {
            forespoerselDao.lagre(forespoersel)
                .let { id ->
                    if (id != null) {
                        logger.info("Forespørsel lagret med id=$id.")
                    } else {
                        logger.error("Forespørsel ble ikke lagret.")
                    }
                }

            priProducer.send(
                Pri.Key.NOTIS to ForespoerselMottatt.notisType.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
                Pri.Key.ORGNR to forespoersel.orgnr.toJson(Orgnr.serializer()),
                Pri.Key.FNR to forespoersel.fnr.toJson()
            )
                .ifTrue { logger.info("Sa ifra om mottatt forespørsel til Simba.") }
                .ifFalse { logger.error("Klarte ikke si ifra om mottatt forespørsel til Simba.") }
        } else {
            "Ignorerer mottatt forespørsel om inntektsmelding siden den gjelder organisasjon uten tillatelse til pilot.".let {
                logger.info(it)
                sikkerlogger.info("$it orgnr=${forespoersel.orgnr}")
            }
        }
    }
}
