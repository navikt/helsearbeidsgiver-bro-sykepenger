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
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.require
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue
import java.util.UUID

class LagreKomplettForespoerselRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid).apply {
            validate { msg ->
                msg.demandValues(Spleis.Key.TYPE to Spleis.Event.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_KOMPLETT.name)
                msg.requireArray(Spleis.Key.SYKMELDINGSPERIODER.verdi) {
                    require(
                        Spleis.Key.FOM to { it.fromJson(LocalDateSerializer) },
                        Spleis.Key.TOM to { it.fromJson(LocalDateSerializer) }
                    )
                }
                msg.requireArray(Spleis.Key.EGENMELDINGSPERIODER.verdi) {
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
        val forespoerselId = randomUuid()

        MdcUtils.withLogFields(
            "forespoerselId" to forespoerselId.toString()
        ) {
            runCatching {
                packet.loesBehov(forespoerselId)
            }
                .onFailure(loggernaut::ukjentFeil)
        }
    }

    private fun JsonMessage.loesBehov(forespoerselId: UUID) {
        loggernaut.aapen.info("Mottok melding av type '${Spleis.Key.TYPE.fra(this).fromJson(String.serializer())}'")
        loggernaut.sikker.info("Mottok melding med innhold:\n${toJson()}")

        val forespoersel = ForespoerselDto(
            forespoerselId = forespoerselId,
            orgnr = Spleis.Key.ORGANISASJONSNUMMER.fra(this).fromJson(Orgnr.serializer()),
            fnr = Spleis.Key.FØDSELSNUMMER.fra(this).fromJson(String.serializer()),
            vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.fra(this).fromJson(UuidSerializer),
            skjaeringstidspunkt = Spleis.Key.SKJÆRINGSTIDSPUNKT.fra(this).fromJson(LocalDateSerializer),
            sykmeldingsperioder = Spleis.Key.SYKMELDINGSPERIODER.fra(this).fromJson(Periode.serializer().list()),
            egenmeldingsperioder = Spleis.Key.EGENMELDINGSPERIODER.fra(this).fromJson(Periode.serializer().list()),
            forespurtData = Spleis.Key.FORESPURT_DATA.fra(this).fromJson(ForespurtDataDto.serializer().list()),
            forespoerselBesvart = null,
            status = Status.AKTIV,
            type = Type.KOMPLETT
        )

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
