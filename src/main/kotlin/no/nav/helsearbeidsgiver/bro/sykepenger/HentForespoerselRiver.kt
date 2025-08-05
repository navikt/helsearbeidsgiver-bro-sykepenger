package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDtoMedEksponertFsp
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
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue

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
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val json = packet.toJson().parseJson()

        logger().info("Mottok melding på pri-topic av type '${Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID}'.")
        logger().info("Mottok melding på pri-topic med innhold:\n${json.toPretty()}")

        val vedtaksperiodeId =
            Pri.Key.VEDTAKSPERIODE_ID.les(
                UuidSerializer,
                json.fromJsonMapFiltered(Pri.Key.serializer()),
            )
        val forespoerselListe = forespoerselDao.hentForespoerslerForVedtaksperiodeId(vedtaksperiodeId)
        if (forespoerselListe.isEmpty()) {
            logger().error("Det er ingen forespørsel for vedtaksperiodeId=$vedtaksperiodeId.")
            return
        } else {
            logger().info("Fant ${forespoerselListe.size} forespørsel(er) for vedtaksperiodeId=$vedtaksperiodeId.")

            forespoerselListe.forEach { it -> sendForespoersel(it) }
        }
    }

    private fun sendForespoersel(forespoersel: ForespoerselDtoMedEksponertFsp) {
        try {
            priProducer
                .send(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FOR_VEDTAKSPERIODE_ID.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to forespoersel.forespoerselId.toJson(),
                    Pri.Key.FORESPOERSEL to ForespoerselSimba(forespoersel).toJson(ForespoerselSimba.serializer()),
                    Pri.Key.EKSPONERT_FORESPOERSEL_ID to forespoersel.finnEksponertForespoerselId().toJson(),
                    Pri.Key.STATUS to forespoersel.getStatus().toJson(),
                ).ifTrue { logger().info("Sa ifra om oppdatert forespørsel til LPS-API.") }
                .ifFalse { logger().error("Klarte ikke å si ifra om oppdatert forespørsel til LPS-API.") }
        } catch (e: Exception) {
            logger().error("Feil ved sending av forespørsel: ${e.message}", e)
        }
    }
}
