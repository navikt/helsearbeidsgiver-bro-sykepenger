package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
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
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

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
                        Spleis.Key.VEDTAKSPERIODE_ID,
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

        val inntektsmeldingHaandtert =
            InntektsmeldingHaandtertDto(
                orgnr = Spleis.Key.ORGANISASJONSNUMMER.les(Orgnr.serializer(), melding),
                fnr = Spleis.Key.FØDSELSNUMMER.les(Fnr.serializer(), melding),
                vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.les(UuidSerializer, melding),
                inntektsmeldingId = inntektsmeldingId,
                haandtert = Spleis.Key.OPPRETTET.les(LocalDateTimeSerializer, melding),
            )

        val aktivForespoersel = forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(inntektsmeldingHaandtert.vedtaksperiodeId)

        val antallOppdaterte =
            forespoerselDao.oppdaterForespoerslerSomBesvartFraSpleis(
                vedtaksperiodeId = inntektsmeldingHaandtert.vedtaksperiodeId,
                besvart = inntektsmeldingHaandtert.haandtert,
                inntektsmeldingId = inntektsmeldingId,
            )

        if (antallOppdaterte > 0) {
            if (aktivForespoersel != null) {
                loggernaut.info("Oppdaterte status til besvart fra Spleis for forespørsel ${aktivForespoersel.forespoerselId}.")
            }

            val forespoerselIdEksponertTilSimba =
                forespoerselDao
                    .hentForespoerslerEksponertTilSimba(setOf(inntektsmeldingHaandtert.vedtaksperiodeId))
                    .firstOrNull()
                    ?.forespoerselId

            if (forespoerselIdEksponertTilSimba == null) {
                loggernaut.aapen.warn("Fant ingen forespørsler for den besvarte inntektsmeldingen")
                loggernaut.sikker.warn("Fant ingen forespørsler for den besvarte inntektsmeldingen: ${toPretty()}")
            } else {
                val felter =
                    listOfNotNull(
                        Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART.toJson(Pri.NotisType.serializer()),
                        Pri.Key.FORESPOERSEL_ID to forespoerselIdEksponertTilSimba.toJson(),
                        inntektsmeldingId?.let { Pri.Key.SPINN_INNTEKTSMELDING_ID to it.toJson() },
                    ).toTypedArray()

                priProducer
                    .send(
                        *felter,
                    ).ifTrue { loggernaut.info("Sa ifra om besvart forespørsel til Simba.") }
                    .ifFalse { loggernaut.error("Klarte ikke si ifra om besvart forespørsel til Simba.") }
            }
        }
    }
}
