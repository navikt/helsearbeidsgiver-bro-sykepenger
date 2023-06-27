package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.interestedKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer

internal class MarkerHaandertForespoerselRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid).apply {
            validate { msg ->
                msg.demandValues(Spleis.Key.TYPE to Spleis.Event.INNTEKTSMELDING_HÅNDTERT.name)
                msg.requireKeys(
                    Spleis.Key.ORGANISASJONSNUMMER,
                    Spleis.Key.FØDSELSNUMMER,
                    Spleis.Key.VEDTAKSPERIODE_ID,
                    Spleis.Key.OPPRETTET
                )
                msg.interestedKeys(Spleis.Key.DOKUMENT_ID)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        loggernaut.aapen.info("Mottok melding på arbeidsgiveropplysninger-topic av type '${Spleis.Key.TYPE.fra(packet).fromJson(String.serializer())}'.")
        loggernaut.sikker.info("Mottok melding på arbeidsgiveropplysninger-topic med innhold:\n${packet.toJson()}")

        val dokumentId = Spleis.Key.DOKUMENT_ID.fraEllerNull(packet)?.fromJson(UuidSerializer)
        val inntektsmeldingHaandtert = InntektsmeldingHaandtertDto(
            orgnr = Spleis.Key.ORGANISASJONSNUMMER.fra(packet).fromJson(Orgnr.serializer()),
            vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.fra(packet).fromJson(UuidSerializer),
            fnr = Spleis.Key.FØDSELSNUMMER.fra(packet).fromJson(String.serializer()),
            dokumentId = dokumentId,
            opprettet = Spleis.Key.OPPRETTET.fra(packet).fromJson(LocalDateTimeSerializer)
        )

        if (inntektsmeldingHaandtert.orgnr in Env.AllowList.organisasjoner) {
            forespoerselDao.oppdaterAktiveForespoerslerSomErBesvart(inntektsmeldingHaandtert.vedtaksperiodeId, Status.BESVART, inntektsmeldingHaandtert.opprettet, dokumentId)

            loggernaut.aapen.info("Oppdaterte forespørselstatus til besvart")
            loggernaut.sikker.info("Oppdaterte forespørselstatus til besvart:\n${packet.toJson()}")
        }
    }
}
