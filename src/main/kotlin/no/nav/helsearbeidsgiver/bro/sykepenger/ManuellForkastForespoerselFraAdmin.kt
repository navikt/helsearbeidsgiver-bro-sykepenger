package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.time.LocalDateTime
import java.util.UUID

/*
  Tar imot melding fra Hag-admin om forespørsel (bruk eksponert forespørselId for å lukke sak og oppgave!) som skal forkastes.
  Typisk må dette gjøres manuelt når inntektsmeldinger fra Altinn2 trigger svar fra spleis på ukjente vedtaksperioder i bro,
  slik at forespørsler som egentlig er besvart og kan lukkes blir stuck med status AKTIV.
 */
class ManuellForkastForespoerselFraAdmin(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer,
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid)
            .apply {
                validate { msg ->
                    msg.demandValues(Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_MANUELT_FORKASTET.name)
                    msg.requireKeys(Pri.Key.FORESPOERSEL_ID)
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

        loggernaut.aapen.info("Mottok melding på pri-topic av type '${Pri.NotisType.FORESPOERSEL_MANUELT_FORKASTET}'.")
        loggernaut.sikker.info("Mottok melding på pri-topic med innhold:\n${json.toPretty()}")

        val forespoerselId =
            Pri.Key.FORESPOERSEL_ID.les(
                UuidSerializer,
                json.fromJsonMapFiltered(Pri.Key.serializer()),
            )
        MdcUtils.withLogFields(
            "forespoerselId" to forespoerselId.toString(),
        ) {
            runCatching {
                markerForkastet(forespoerselId)
            }.onFailure(loggernaut::ukjentFeil)
        }
    }

    private fun markerForkastet(forespoerselId: UUID) {
        val vedtaksperiodeId = forespoerselDao.hentVedtaksperiodeId(forespoerselId)

        if (vedtaksperiodeId != null) {
            val oppdaterte =
                forespoerselDao.oppdaterForespoerslerSomForkastet(vedtaksperiodeId)
            if (oppdaterte.isNotEmpty()) {
                loggernaut.info(
                    "Oppdaterte: ${oppdaterte.size} forespørsler: Satt status manuelt til forkastet for forespørsel $forespoerselId.",
                )
                priProducer.send(
                    vedtaksperiodeId,
                    Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_FORKASTET.toJson(Pri.NotisType.serializer()),
                    Pri.Key.SENDT_TID to LocalDateTime.now().truncMillis().toJson(),
                    Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                )
                loggernaut.info("Sa ifra på pri-topic om forkastet forespørsel med forespørselId: $forespoerselId")
                // synkroniser til LPSAPI, fordi den eksponerte forespørselId brukes mot Simba for å lukke sak, men kan allerede være forkastet i bro og api
                priProducer.send(
                    vedtaksperiodeId,
                    Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID.toJson(Pri.BehovType.serializer()),
                    Pri.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toJson(),
                )
                loggernaut.info("Trigget synkronisering av forespørsler for LPS-API for vedtaksperiodeId: $vedtaksperiodeId")
            } else {
                loggernaut.warn("Fant ingen aktive forespørsler med forespørselId: $forespoerselId")
            }
        } else {
            loggernaut.warn("Fant ingen forespørsel å markere som forkastet, forespørselId: $forespoerselId")
        }
    }
}
