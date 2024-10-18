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

// Lytter på event om at vedtaksperiode er kastet til Infotrygd
internal class ForkastVedtaksperiodeRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer,
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid)
            .apply {
                validate { msg ->
                    msg.demandValues(Spleis.Key.TYPE to Spleis.Event.VEDTAKSPERIODE_FORKASTET.name)
                    msg.requireKeys(
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
                .oppdaterForespoersler()
        }.onFailure(loggernaut::ukjentFeil)
            .getOrThrow()
    }

    private fun JsonElement.oppdaterForespoersler() {
        val melding = fromJsonMapFiltered(Spleis.Key.serializer())

        loggernaut.aapen.info(
            "Mottok melding på arbeidsgiveropplysninger-topic av type '${Spleis.Event.VEDTAKSPERIODE_FORKASTET}'.",
        )
        loggernaut.sikker.info("Mottok melding på arbeidsgiveropplysninger-topic med innhold:\n${toPretty()}")

        val vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.les(UuidSerializer, melding)

        val forespoersel = forespoerselDao.hentForespoerslerEksponertTilSimba(listOf(vedtaksperiodeId)).firstOrNull()

        if (forespoersel != null) {
            forespoerselDao.markerKastetTilInfotrygd(vedtaksperiodeId)

            priProducer
                .send(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_KASTET_TIL_INFOTRYGD.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
                ).ifTrue { loggernaut.aapen.info("Sa ifra til Simba om forespørsel kastet til Infotrygd.") }
                .ifFalse { loggernaut.aapen.error("Klarte ikke si ifra til Simba om forespørsel kastet til Infotrygd.") }
        }
    }
}
