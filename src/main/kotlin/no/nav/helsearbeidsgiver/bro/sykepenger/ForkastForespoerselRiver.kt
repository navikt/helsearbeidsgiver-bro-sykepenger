package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

// Lytter på event om at forespørsel ikke er nødvendig lenger og forkaster forespørselen
internal class ForkastForespoerselRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer,
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid)
            .apply {
                validate { msg ->
                    msg.demandValues(Spleis.Key.TYPE to Spleis.Event.TRENGER_IKKE_OPPLYSNINGER_FRA_ARBEIDSGIVER.name)
                    msg.requireKeys(
                        Spleis.Key.ORGANISASJONSNUMMER,
                        Spleis.Key.VEDTAKSPERIODE_ID,
                    )
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
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

        loggernaut.aapen.info(
            "Mottok melding på arbeidsgiveropplysninger-topic av type '${Spleis.Event.TRENGER_IKKE_OPPLYSNINGER_FRA_ARBEIDSGIVER}'.",
        )
        loggernaut.sikker.info("Mottok melding på arbeidsgiveropplysninger-topic med innhold:\n${toPretty()}")

        val orgnummer = Spleis.Key.ORGANISASJONSNUMMER.les(Orgnr.serializer(), melding)
        val vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.les(UuidSerializer, melding)

        val forespoersel = forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(vedtaksperiodeId)

        if (forespoersel != null) {
            forespoerselDao.oppdaterForespoerslerSomForkastet(vedtaksperiodeId)
            "Oppdaterte status til forkastet for forespørsel ${forespoersel.forespoerselId}.".also {
                loggernaut.aapen.info(it)
                loggernaut.sikker.info(it)
            }

            priProducer
                .send(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FORKASTET.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
                ).ifTrue { loggernaut.aapen.info("Sa ifra om forkastet forespørsel til Simba.") }
                .ifFalse { loggernaut.aapen.error("Klarte ikke si ifra om forkastet forespørsel til Simba.") }
        }
    }
}
