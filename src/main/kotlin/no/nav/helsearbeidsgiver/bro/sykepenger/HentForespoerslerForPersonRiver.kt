package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

/*
 Henter alle eksponerte forespørsler for et gitt fnr, sortert per orgnr og tid opprettet.
 Brukes for at HAG og evt NKS enklere kan finne åpne saker og oppgaver som skal lukkes
  (sak /oppgave har samme ID som eksponert fsp-id)
  Returner tom liste hvis ingen treff, slik at klient / kallende kode vet at det faktisk har kommet et svar.
 */
class HentForespoerslerForPersonRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer,
) : River.PacketListener {
    init {

        River(rapid)
            .apply {
                validate { msg ->
                    msg.demandValues(Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_PERSON.name)
                    msg.requireKeys(Pri.Key.FNR, Pri.Key.RESPONS_ID)
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

        logger().info("Mottok melding på pri-topic av type '${Pri.BehovType.HENT_FORESPOERSLER_FOR_PERSON}'.")
        sikkerLogger().info("Mottok melding på pri-topic med innhold:\n${json.toPretty()}")
        val fnr: Fnr
        val responsId: UUID
        try {
            fnr =
                Pri.Key.FNR.les(
                    Fnr.serializer(),
                    json.fromJsonMapFiltered(Pri.Key.serializer()),
                )
            responsId =
                Pri.Key.RESPONS_ID.les(
                    UuidSerializer,
                    json.fromJsonMapFiltered(Pri.Key.serializer()),
                )
        } catch (ex: Exception) {
            sikkerLogger().error("Klarte ikke å lese fnr fra melding: ${ex.message}", ex)
            return
        }
        val forespoerselListe = forespoerselDao.hentEksponerteForespoerslerForPerson(fnr)
        if (forespoerselListe.isEmpty()) {
            sikkerLogger().info("Fant ingen forespørsel for fnr=$fnr.")
        } else {
            sikkerLogger().info("Fant ${forespoerselListe.size} forespørsel(er) for fnr=$fnr.")
        }
        sendForespoerselListe(forespoerselListe, responsId)
    }

    private fun sendForespoerselListe(
        forespoerselListe: List<ForespoerselDto>,
        responsId: UUID,
    ) {
        try {
            val liste =
                forespoerselListe.map { forespoerselDto -> ForespoerselSimba(forespoerselDto) }.toJson(ForespoerselSimba.serializer())
            val melding =
                arrayOf(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_LISTE_FOR_PERSON.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_LISTE to liste,
                )
            priProducer.send(responsId, *melding)
        } catch (e: Exception) {
            logger().error("Feil ved sending av forespørsel: ${e.message}", e)
        }
    }
}
