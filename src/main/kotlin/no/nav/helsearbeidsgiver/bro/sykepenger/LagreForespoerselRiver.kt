package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.require
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue

class LagreForespoerselRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer
) : River.PacketListener {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

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
                    Spleis.Key.SKJÆRINGSTIDSPUNKT,
                    Spleis.Key.FORESPURT_DATA
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottok melding av type '${Spleis.Key.TYPE.fra(packet).fromJson(String.serializer())}'")
        sikkerLogger.info("Mottok melding med innhold:\n${packet.toJson()}")

        val forespoersel = ForespoerselDto(
            forespoerselId = randomUuid(),
            orgnr = Spleis.Key.ORGANISASJONSNUMMER.fra(packet).fromJson(Orgnr.serializer()),
            fnr = Spleis.Key.FØDSELSNUMMER.fra(packet).fromJson(String.serializer()),
            vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.fra(packet).fromJson(UuidSerializer),
            skjaeringstidspunkt = Spleis.Key.SKJÆRINGSTIDSPUNKT.fra(packet).fromJson(LocalDateSerializer),
            sykmeldingsperioder = Spleis.Key.SYKMELDINGSPERIODER.fra(packet).fromJson(Periode.serializer().list()),
            forespurtData = Spleis.Key.FORESPURT_DATA.fra(packet).fromJson(ForespurtDataDto.serializer().list()),
            forespoerselBesvart = null,
            status = Status.AKTIV,
            type = Type.KOMPLETT
        )

        sikkerLogger.info("Forespoersel lest: $forespoersel")

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
                sikkerLogger.info("$it orgnr=${forespoersel.orgnr}")
            }
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        "Innkommende melding har feil.".let {
            logger.info("$it Se sikker logg for mer info.")
            sikkerLogger.info("$it Detaljer:\n${problems.toExtendedReport()}")
        }
    }
}
