package no.nav.helsearbeidsgiver.bro.sykepenger

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.time.LocalDateTime
import java.util.UUID

class MarkerBesvartFraSimbaRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid).apply {
            validate { msg ->
                msg.demandValues(Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_BESVART_SIMBA.name)
                msg.requireKeys(Pri.Key.FORESPOERSEL_ID)
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val json = packet.toJson().parseJson()

        loggernaut.aapen.info("Mottok melding på pri-topic av type '${Pri.NotisType.FORESPOERSEL_BESVART_SIMBA}'.")
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
                markerBesvart(forespoerselId)
            }
                .onFailure(loggernaut::ukjentFeil)
        }
    }

    private fun markerBesvart(forespoerselId: UUID) {
        val forespoersel = forespoerselDao.hentForespoerselForForespoerselId(forespoerselId)

        if (forespoersel != null) {
            val antallOppdaterte =
                forespoerselDao.oppdaterForespoerslerSomBesvartFraSimba(
                    vedtaksperiodeId = forespoersel.vedtaksperiodeId,
                    besvart = LocalDateTime.now(),
                )

            if (antallOppdaterte > 0) {
                if (forespoersel.status == Status.AKTIV) {
                    loggernaut.info("Oppdaterte status til besvart fra Simba for forespørsel ${forespoersel.forespoerselId}.")
                }
            } else {
                loggernaut.error("Ingen forespørsler ble markert som besvart fra Simba.")
            }
        } else {
            loggernaut.error("Fant ingen forespørsel å markere som besvart fra Simba.")
        }
    }
}
