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
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.rejectKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.require
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

// Tilgjengeliggjør hvilke data spleis forespør fra arbeidsgiver
class TilgjengeliggjoerForespoerselRiver(
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
                        Pri.Key.BEHOV to ForespoerselSvar.behovType.name,
                    )
                    msg.require(
                        Pri.Key.FORESPOERSEL_ID to { it.fromJson(UuidSerializer) },
                    )
                    msg.requireKeys(Pri.Key.BOOMERANG)
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

        val forespoerselId =
            Pri.Key.FORESPOERSEL_ID.les(
                UuidSerializer,
                json.fromJsonMapFiltered(Pri.Key.serializer()),
            )

        MdcUtils.withLogFields(
            "forespoerselId" to forespoerselId.toString(),
        ) {
            runCatching {
                json.sendSvar(forespoerselId)
            }.onFailure(loggernaut::ukjentFeil)
        }
    }

    private fun JsonElement.sendSvar(forespoerselId: UUID) {
        val melding = fromJsonMapFiltered(Pri.Key.serializer())

        loggernaut.aapen.info("Mottok melding på pri-topic av type '${Pri.Key.BEHOV.les(String.serializer(), melding)}'.")
        loggernaut.sikker.info("Mottok melding på pri-topic med innhold:\n${toPretty()}")

        val forespoerselSvarJson =
            ForespoerselSvar(
                forespoerselId = forespoerselId,
                boomerang = Pri.Key.BOOMERANG.les(JsonElement.serializer(), melding),
            ).let {
                val forespoersel =
                    forespoerselDao
                        .hentVedtaksperiodeId(forespoerselId)
                        ?.let { vedtaksperiodeId ->
                            forespoerselDao.hentForespoerslerEksponertTilSimba(setOf(vedtaksperiodeId))
                        }?.firstOrNull()

                if (forespoersel != null) {
                    loggernaut.aapen.info("Forespørsel funnet.")
                    it.copy(resultat = ForespoerselSimba(forespoersel))
                } else {
                    loggernaut.aapen.info("Forespørsel _ikke_ funnet.")
                    it.copy(feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET)
                }
            }.toJson(ForespoerselSvar.serializer())

        priProducer.send(
            Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to forespoerselSvarJson,
        )

        loggernaut.aapen.info("Behov besvart på pri-topic med forespørsel.")
        loggernaut.sikker.info("Behov besvart på pri-topic med forespørsel: ${forespoerselSvarJson.toPretty()}")
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        loggernaut.innkommendeMeldingFeil(problems)
    }
}
