package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.HentForespoerslerForVedtaksperiodeIderSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.rejectKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.vedtaksperiodeListeSerializer
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.util.UUID

// Tilgjengeliggjør aktive forespørsler på vedtaksperiode-IDer
class TilgjengeliggjoerForespoerslerForVedtaksperiodeIderRiver(
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
                        Pri.Key.BEHOV to HentForespoerslerForVedtaksperiodeIderSvar.behovType.name,
                    )
                    msg.requireKeys(Pri.Key.BOOMERANG, Pri.Key.VEDTAKSPERIODE_ID_LISTE)
                    msg.rejectKeys(Pri.Key.LØSNING)
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val json = packet.toJson().parseJson()

        val vedtaksperiodeIder =
            Pri.Key.VEDTAKSPERIODE_ID_LISTE.les(
                vedtaksperiodeListeSerializer,
                json.fromJsonMapFiltered(Pri.Key.serializer()),
            )

        runCatching {
            json.sendSvar(vedtaksperiodeIder)
        }.onFailure(loggernaut::ukjentFeil)
    }

    private fun JsonElement.sendSvar(vedtaksperiodeIder: List<UUID>) {
        val melding = fromJsonMapFiltered(Pri.Key.serializer())

        "Mottok melding på pri-topic".also {
            loggernaut.aapen.info("$it av type '${Pri.Key.BEHOV.les(String.serializer(), melding)}'.")
            loggernaut.sikker.info("$it med innhold:\n${toPretty()}")
        }

        val forespoersler = vedtaksperiodeIder.mapNotNull { forespoerselDao.hentForespoerselEksponertTilSimba(it) }

        val hentForespoerslerForVedtaksperiodeIderSvarJson =
            HentForespoerslerForVedtaksperiodeIderSvar(
                resultat = forespoersler.map(::ForespoerselSimba),
                boomerang = Pri.Key.BOOMERANG.les(JsonElement.serializer(), melding),
            ).toJson(HentForespoerslerForVedtaksperiodeIderSvar.serializer())

        priProducer.send(
            Pri.Key.BEHOV to HentForespoerslerForVedtaksperiodeIderSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to hentForespoerslerForVedtaksperiodeIderSvarJson,
        )

        "Behov besvart på pri-topic med liste av forespørsler".also {
            loggernaut.aapen.info("$it.")
            loggernaut.sikker.info("$it: ${hentForespoerslerForVedtaksperiodeIderSvarJson.toPretty()}")
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
    ) {
        loggernaut.innkommendeMeldingFeil(problems)
    }
}
