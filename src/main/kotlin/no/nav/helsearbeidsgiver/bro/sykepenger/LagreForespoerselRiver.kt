package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.LocalDateSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.UuidSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.fromJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.ifFalse
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.ifTrue
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.list
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.require
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.sikkerLogger
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson
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
            validate { msg ->
                msg.demandValues(Spleis.Key.TYPE to Spleis.Event.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER.name)
                msg.requireArray(Spleis.Key.SYKMELDINGSPERIODER.verdi) {
                    require(
                        Spleis.Key.FOM to { it.fromJson(LocalDateSerializer) },
                        Spleis.Key.TOM to { it.fromJson(LocalDateSerializer) }
                    )
                }
                msg.requireKeys(
                    Spleis.Key.ORGANISASJONSNUMMER,
                    Spleis.Key.FØDSELSNUMMER,
                    Spleis.Key.VEDTAKSPERIODE_ID,
                    Spleis.Key.FORESPURT_DATA
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding av type '${Spleis.Key.TYPE.fra(packet).fromJson(String.serializer())}'")
        sikkerlogger.info("Mottok melding med innhold:\n${packet.toJson()}")

        val forespoersel = ForespoerselDto(
            forespoerselId = randomUuid(),
            orgnr = Spleis.Key.ORGANISASJONSNUMMER.fra(packet).fromJson(String.serializer()),
            fnr = Spleis.Key.FØDSELSNUMMER.fra(packet).fromJson(String.serializer()),
            vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.fra(packet).fromJson(UuidSerializer),
            sykmeldingsperioder = Spleis.Key.SYKMELDINGSPERIODER.fra(packet).fromJson(Periode.serializer().list()),
            forespurtData = Spleis.Key.FORESPURT_DATA.fra(packet).fromJson(ForespurtDataDto.serializer().list()),
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
            Pri.Key.NOTIS to ForespoerselMottatt.notisType.toJson(Pri.NotisType.serializer()),
            Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
            Pri.Key.ORGNR to forespoersel.orgnr.toJson(),
            Pri.Key.FNR to forespoersel.fnr.toJson()
        )
            .ifTrue { logger.info("Sa ifra om mottatt forespørsel til Simba.") }
            .ifFalse { logger.info("Klarte ikke si ifra om mottatt forespørsel til Simba.") }
    }
}
