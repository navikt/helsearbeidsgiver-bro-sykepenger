package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.HentForespoerslerForVedtaksperiodeIdListeSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.rejectKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.util.UUID

// Tilgjengeliggjør eksponerte forespørsler for en liste med vedtaksperiode-IDer
class TilgjengeliggjoerForespoerslerForVedtaksperiodeIdListeRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer,
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid)
            .apply {
                validate { msg ->
                    msg.demandValues(
                        Pri.Key.BEHOV to HentForespoerslerForVedtaksperiodeIdListeSvar.behovType.name,
                    )
                    msg.requireKeys(Pri.Key.BOOMERANG, Pri.Key.VEDTAKSPERIODE_ID_LISTE)
                    msg.rejectKeys(Pri.Key.LØSNING)
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val json = packet.toJson().parseJson()

        val vedtaksperiodeIdListe =
            Pri.Key.VEDTAKSPERIODE_ID_LISTE.les(
                UuidSerializer.set(),
                json.fromJsonMapFiltered(Pri.Key.serializer()),
            )

        runCatching {
            json.sendSvar(vedtaksperiodeIdListe)
        }.onFailure(loggernaut::ukjentFeil)
    }

    private fun JsonElement.sendSvar(vedtaksperiodeIdListe: Set<UUID>) {
        val melding = fromJsonMapFiltered(Pri.Key.serializer())

        "Mottok melding på pri-topic".also {
            loggernaut.aapen.info("$it av type '${Pri.Key.BEHOV.les(String.serializer(), melding)}'.")
            loggernaut.sikker.info("$it med innhold:\n${toPretty()}")
        }

        val forespoersler = forespoerselDao.hentForespoerslerEksponertTilSimba(vedtaksperiodeIdListe)

        val hentForespoerslerForVedtaksperiodeIdListeSvarJson =
            HentForespoerslerForVedtaksperiodeIdListeSvar(
                resultat = forespoersler.map(::ForespoerselSimba),
                boomerang = Pri.Key.BOOMERANG.les(JsonElement.serializer(), melding),
            ).toJson(HentForespoerslerForVedtaksperiodeIdListeSvar.serializer())

        priProducer.send(
            Pri.Key.BEHOV to HentForespoerslerForVedtaksperiodeIdListeSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to hentForespoerslerForVedtaksperiodeIdListeSvarJson,
        )

        "Behov besvart på pri-topic med liste av forespørsler".also {
            loggernaut.aapen.info("$it.")
            loggernaut.sikker.info("$it: ${hentForespoerslerForVedtaksperiodeIdListeSvarJson.toPretty()}")
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        loggernaut.innkommendeMeldingFeil(problems)
    }
}
