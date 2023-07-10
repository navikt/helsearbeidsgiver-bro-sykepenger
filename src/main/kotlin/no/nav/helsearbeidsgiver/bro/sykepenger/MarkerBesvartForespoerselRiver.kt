package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
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
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.lesOrNull
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty

internal class MarkerBesvartForespoerselRiver(
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
        runCatching {
            packet.toJson()
                .parseJson()
                .oppdaterForespoersel()
        }
            .onFailure(loggernaut::ukjentFeil)
            .getOrThrow()
    }

    private fun JsonElement.oppdaterForespoersel() {
        val melding = fromJsonMapFiltered(Spleis.Key.serializer())

        loggernaut.aapen.info("Mottok melding på arbeidsgiveropplysninger-topic av type '${Spleis.Key.TYPE.les(String.serializer(), melding)}'.")
        loggernaut.sikker.info("Mottok melding på arbeidsgiveropplysninger-topic med innhold:\n${toPretty()}")

        val inntektsmeldingId = Spleis.Key.DOKUMENT_ID.lesOrNull(UuidSerializer, melding)

        val inntektsmeldingHaandtert = InntektsmeldingHaandtertDto(
            orgnr = Spleis.Key.ORGANISASJONSNUMMER.les(Orgnr.serializer(), melding),
            fnr = Spleis.Key.FØDSELSNUMMER.les(String.serializer(), melding),
            vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.les(UuidSerializer, melding),
            inntektsmeldingId = inntektsmeldingId,
            haandtert = Spleis.Key.OPPRETTET.les(LocalDateTimeSerializer, melding)
        )

        if (inntektsmeldingHaandtert.orgnr in Env.AllowList.organisasjoner) {
            forespoerselDao.oppdaterForespoerslerSomBesvart(inntektsmeldingHaandtert.vedtaksperiodeId, Status.BESVART, inntektsmeldingHaandtert.haandtert, inntektsmeldingId)

            loggernaut.aapen.info("Oppdaterte forespørselstatus til besvart")
            loggernaut.sikker.info("Oppdaterte forespørselstatus til besvart:\n${toPretty()}")
        }
    }
}
