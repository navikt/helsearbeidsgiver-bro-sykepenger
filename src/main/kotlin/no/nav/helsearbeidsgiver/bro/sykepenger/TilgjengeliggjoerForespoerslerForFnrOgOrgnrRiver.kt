package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.HentForespoerslerForFnrOgOrgnrSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.rejectKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

// Tilgjengeliggjør aktive forespørsler på fnr og orgnr
class TilgjengeliggjoerForespoerslerForFnrOgOrgnrRiver(
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
                        Pri.Key.BEHOV to HentForespoerslerForFnrOgOrgnrSvar.behovType.name,
                    )
                    msg.requireKeys(Pri.Key.BOOMERANG, Pri.Key.FNR, Pri.Key.ORGNR)
                    msg.rejectKeys(Pri.Key.LØSNING)
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val json = packet.toJson().parseJson()

        val orgnr =
            Pri.Key.ORGNR.les(
                Orgnr.serializer(),
                json.fromJsonMapFiltered(Pri.Key.serializer()),
            )
        val fnr =
            Pri.Key.FNR.les(
                Fnr.serializer(),
                json.fromJsonMapFiltered(Pri.Key.serializer()),
            )

        runCatching {
            json.sendSvar(orgnr, fnr)
        }.onFailure(loggernaut::ukjentFeil)
    }

    private fun JsonElement.sendSvar(
        orgnr: Orgnr,
        fnr: Fnr,
    ) {
        val melding = fromJsonMapFiltered(Pri.Key.serializer())

        "Mottok melding på pri-topic".also {
            loggernaut.aapen.info("$it av type '${Pri.Key.BEHOV.les(String.serializer(), melding)}'.")
            loggernaut.sikker.info("$it med innhold:\n${toPretty()}")
        }

        val forespoersler = forespoerselDao.hentAktiveForespoerslerForOrgnrOgFnr(orgnr, fnr)

        val hentForespoerslerForFnrOgOrgnrSvarJson =
            HentForespoerslerForFnrOgOrgnrSvar(
                orgnr = orgnr,
                fnr = fnr,
                resultat = forespoersler.map(::ForespoerselSimba),
                boomerang = Pri.Key.BOOMERANG.les(JsonElement.serializer(), melding),
            ).toJson(HentForespoerslerForFnrOgOrgnrSvar.serializer())

        priProducer.send(
            Pri.Key.BEHOV to HentForespoerslerForFnrOgOrgnrSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to hentForespoerslerForFnrOgOrgnrSvarJson,
        )

        "Behov besvart på pri-topic med liste av forespørsler".also {
            loggernaut.aapen.info("$it.")
            loggernaut.sikker.info("$it: ${hentForespoerslerForFnrOgOrgnrSvarJson.toPretty()}")
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
    ) {
        loggernaut.innkommendeMeldingFeil(problems)
    }
}
