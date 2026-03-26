package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
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
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.time.LocalDateTime

class MarkerBesvartFraSpleisRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer,
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid)
            .apply {
                validate { msg ->
                    msg.demandValues(Spleis.Key.TYPE to Spleis.Event.INNTEKTSMELDING_HÅNDTERT.name)
                    msg.requireKeys(
                        Spleis.Key.ORGANISASJONSNUMMER,
                        Spleis.Key.FØDSELSNUMMER,
                        Spleis.Key.VEDTAKSPERIODE_IDER_MED_SAMME_FRAVAERSDAG,
                        Spleis.Key.OPPRETTET,
                    )
                    msg.interestedKeys(Spleis.Key.DOKUMENT_ID)
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        runCatching {
            packet
                .toJson()
                .parseJson()
                .oppdaterForespoersel()
        }.onFailure(loggernaut::ukjentFeil)
            .getOrThrow()
    }

    private fun JsonElement.oppdaterForespoersel() {
        val melding = fromJsonMapFiltered(Spleis.Key.serializer())

        loggernaut.aapen.info("Mottok melding på arbeidsgiveropplysninger-topic av type '${Spleis.Event.INNTEKTSMELDING_HÅNDTERT}'.")
        loggernaut.sikker.info("Mottok melding på arbeidsgiveropplysninger-topic med innhold:\n${toPretty()}")

        val inntektsmeldingId = Spleis.Key.DOKUMENT_ID.lesOrNull(UuidSerializer, melding)

        val vedtaksperioder =
            Spleis.Key.VEDTAKSPERIODE_IDER_MED_SAMME_FRAVAERSDAG
                .les(
                    UuidSerializer.set(),
                    melding,
                )
        val besvartTid = Spleis.Key.OPPRETTET.les(LocalDateTimeSerializer, melding)
        val forespoerselIderEksponertTilSimba =
            forespoerselDao
                .hentForespoerslerEksponertTilSimba(vedtaksperioder) // Denne henter bare nyeste! Kan være flere "gamle"
                .map { it.forespoerselId }

        val antallOppdaterte =
            vedtaksperioder.sumOf {
                forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                    vedtaksperiodeId = it,
                    besvart = besvartTid,
                    inntektsmeldingId = inntektsmeldingId,
                )
            }
        loggernaut.info("Fant og oppdaterte $antallOppdaterte forespørsler basert på vedtaksperioder: $vedtaksperioder.")
        loggernaut.info("Fant ${forespoerselIderEksponertTilSimba.size} eksponerte forespørsler")

        forespoerselIderEksponertTilSimba.forEach { forespoerselId ->
            val felter =
                listOfNotNull(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Pri.Key.SENDT_TID to LocalDateTime.now().toJson(),
                    inntektsmeldingId?.let { Pri.Key.SPINN_INNTEKTSMELDING_ID to it.toJson() },
                ).toTypedArray()

            priProducer.send(forespoerselId, *felter)

            loggernaut.info("Sa ifra om besvart forespørsel $forespoerselId til Simba.")
        }

        if (antallOppdaterte == 0) {
            loggernaut.aapen.info("Ingen forespørsel funnet, sannsynligvis kom IM før søknad / forespørsel")
            loggernaut.sikker.info(
                "Ingen forespørsel funnet, sannsynligvis kom IM før søknad / forespørsel. Melding: $melding",
            )
        } else {
            // Synkroniserer mot LPS-API
            vedtaksperioder.forEach { vedtaksperiode ->
                priProducer.send(
                    vedtaksperiode,
                    Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID.toJson(Pri.BehovType.serializer()),
                    Pri.Key.VEDTAKSPERIODE_ID to vedtaksperiode.toJson(),
                )
            }
        }
    }
}
