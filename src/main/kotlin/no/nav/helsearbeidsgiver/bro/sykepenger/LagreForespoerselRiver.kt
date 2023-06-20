package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue
import java.util.UUID

sealed class LagreForespoerselRiver(
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer
) : River.PacketListener {
    abstract val loggernaut: Loggernaut<*>

    abstract fun lesForespoersel(forespoerselId: UUID, packet: JsonMessage): ForespoerselDto

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val forespoerselId = randomUuid()

        MdcUtils.withLogFields(
            "forespoerselId" to forespoerselId.toString()
        ) {
            runCatching {
                packet.loesBehov(forespoerselId)
            }
                .onFailure(loggernaut::ukjentFeil)
                .getOrThrow()
        }
    }

    private fun JsonMessage.loesBehov(forespoerselId: UUID) {
        loggernaut.aapen.info("Mottok melding av type '${Spleis.Key.TYPE.fra(this).fromJson(String.serializer())}'")
        loggernaut.sikker.info("Mottok melding med innhold:\n${toJson()}")

        val forespoersel = lesForespoersel(forespoerselId, this)
        loggernaut.sikker.info("Forespoersel lest: $forespoersel")

        if (forespoersel.orgnr in Env.AllowList.organisasjoner) {
            forespoerselDao.lagre(forespoersel)
                .let { id ->
                    if (id != null) {
                        loggernaut.aapen.info("Forespørsel lagret med id=$id.")
                    } else {
                        loggernaut.aapen.error("Forespørsel ble ikke lagret.")
                    }
                }

            priProducer.send(
                Pri.Key.NOTIS to ForespoerselMottatt.notisType.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
                Pri.Key.ORGNR to forespoersel.orgnr.toJson(Orgnr.serializer()),
                Pri.Key.FNR to forespoersel.fnr.toJson()
            )
                .ifTrue { loggernaut.aapen.info("Sa ifra om mottatt forespørsel til Simba.") }
                .ifFalse { loggernaut.aapen.error("Klarte ikke si ifra om mottatt forespørsel til Simba.") }
        } else {
            "Ignorerer mottatt forespørsel om inntektsmelding siden den gjelder organisasjon uten tillatelse til pilot.".let {
                loggernaut.aapen.info(it)
                MdcUtils.withLogFields(Pri.Key.ORGNR.verdi to forespoersel.orgnr.verdi) {
                    loggernaut.sikker.info(it)
                }
            }
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggernaut.innkommendeMeldingFeil(problems)
    }
}
