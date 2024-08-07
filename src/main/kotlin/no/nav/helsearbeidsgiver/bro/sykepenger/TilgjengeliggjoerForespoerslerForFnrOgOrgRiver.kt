package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.HentForespoerslerSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Suksess
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

// Tilgjengeliggjør aktive forespørsler på fnr og orgnr
class TilgjengeliggjoerForespoerslerForFnrOgOrgRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer,
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid).apply {
            validate { msg ->
                msg.demandValues(
                    Pri.Key.BEHOV to HentForespoerslerSvar.behovType.name,
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
                String.serializer(),
                json.fromJsonMapFiltered(Pri.Key.serializer()),
            )

        runCatching {
            json.sendSvar(orgnr, fnr)
        }
            .onFailure(loggernaut::ukjentFeil)
    }

    private fun JsonElement.sendSvar(
        orgnr: Orgnr,
        fnr: String,
    ) {
        val melding = fromJsonMapFiltered(Pri.Key.serializer())

        val forespoersler = forespoerselDao.hentAktiveForespoerslerForOrgnrOgFnr(orgnr, fnr)

        loggernaut.aapen.info("Mottok melding på pri-topic av type '${Pri.Key.BEHOV.les(String.serializer(), melding)}'.")
        loggernaut.sikker.info("Mottok melding på pri-topic med innhold:\n${toPretty()}")

        val hentForespoerslerSvarJson =
            HentForespoerslerSvar(
                orgnr = orgnr,
                fnr = fnr,
                resultat = forespoersler.map(::Suksess),
                boomerang = Pri.Key.BOOMERANG.les(JsonElement.serializer(), melding),
            ).toJson(HentForespoerslerSvar.serializer())

        priProducer.send(
            Pri.Key.BEHOV to HentForespoerslerSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to hentForespoerslerSvarJson,
        )

        loggernaut.aapen.info("Behov besvart på pri-topic med forespørsel liste.")
        loggernaut.sikker.info("Behov besvart på pri-topic med forespørsel liste: ${hentForespoerslerSvarJson.toPretty()}")
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
    ) {
        loggernaut.innkommendeMeldingFeil(problems)
    }
}
