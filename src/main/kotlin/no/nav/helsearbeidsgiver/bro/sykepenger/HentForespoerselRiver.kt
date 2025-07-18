package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger

class HentForespoerselRiver(
    rapid: RapidsConnection,
    private val priProducer: PriProducer,
    private val forespoerselDao: ForespoerselDao,
) : River.PacketListener {
    init {

        River(rapid)
            .apply {
                validate { msg ->
                    msg.demandValues(Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID.name)
                    msg.requireKeys(Pri.Key.VEDTAKSPERIODE_ID)
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val json = packet.toJson().parseJson()

        logger().info("Mottok melding på pri-topic av type '${Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID}'.")
        logger().info("Mottok melding på pri-topic med innhold:\n${json.toPretty()}")

        val vedtaksperiodeId =
            Pri.Key.VEDTAKSPERIODE_ID.les(
                UuidSerializer,
                json.fromJsonMapFiltered(Pri.Key.serializer()),
            )
        val forespoersel = forespoerselDao.hentForespoerslerForVedtaksperiodeId(vedtaksperiodeId)
        if (forespoersel.isEmpty()) {
            logger().error("Det er ingen forespørsel for vedtaksperiodeId=$vedtaksperiodeId.")
            return
        } else {
            logger().info("Fant ${forespoersel.size} forespørsel(er) for vedtaksperiodeId=$vedtaksperiodeId.")

            // val forespoerselDto = forespoersel.get(0)
//            priProducer
//                .send(
//                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FOR_VEDTAKSPERIODE_ID.toJson(Pri.NotisType.serializer()),
//                    Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
//                    Pri.Key.ORGNR to forespoersel.orgnr.toJson(Orgnr.serializer()),
//                    Pri.Key.FNR to forespoersel.fnr.toJson(Fnr.serializer()),
//                    Pri.Key.FORESPOERSEL to ForespoerselSimba(forespoersel).toJson(ForespoerselSimba.serializer()),
//                    Pri.Key.EKSPONERT_FORESPOERSEL_ID to forespoersel.eksponertForespoerselId!!.toJson(),
//                ).ifTrue { logger().info("Sa ifra om oppdatert forespørsel til LPS-API.") }
//                .ifFalse { logger().error("Klarte ikke å si ifra om oppdatert forespørsel til LPS-API.") }
        }
    }
}
