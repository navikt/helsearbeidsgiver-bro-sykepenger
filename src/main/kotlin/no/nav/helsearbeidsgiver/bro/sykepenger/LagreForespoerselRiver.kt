package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Log
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

sealed class LagreForespoerselRiver(
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer,
) : River.PacketListener {
    abstract val loggernaut: Loggernaut<*>

    abstract fun lesForespoersel(
        forespoerselId: UUID,
        melding: Map<Spleis.Key, JsonElement>,
    ): ForespoerselDto

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val forespoerselId = randomUuid()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.forespoerselId(forespoerselId),
        ) {
            runCatching {
                packet
                    .toJson()
                    .parseJson()
                    .lagreForespoersel(forespoerselId)
            }.onFailure(loggernaut::ukjentFeil)
                .getOrElse {
                    loggernaut.error("Klarte ikke lagre forespørsel!", it)
                }
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        loggernaut.innkommendeMeldingFeil(problems)
    }

    private fun JsonElement.lagreForespoersel(forespoerselId: UUID) {
        val melding = fromJsonMapFiltered(Spleis.Key.serializer())

        loggernaut.aapen.info("Mottok melding av type '${Spleis.Key.TYPE.les(String.serializer(), melding)}'")
        loggernaut.sikker.info("Mottok melding med innhold:\n${toPretty()}")

        val nyForespoersel = lesForespoersel(forespoerselId, melding)
        loggernaut.sikker.info("Forespoersel lest:\n$nyForespoersel")

        MdcUtils.withLogFields(
            Log.type(nyForespoersel.type),
            Log.vedtaksperiodeId(nyForespoersel.vedtaksperiodeId),
        ) {
            lagreForespoerselHvisIkkeDuplikat(nyForespoersel)
        }
    }

    private fun lagreForespoerselHvisIkkeDuplikat(nyForespoersel: ForespoerselDto) {
        val aktivForespoersel = forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(nyForespoersel.vedtaksperiodeId)

        when {
            aktivForespoersel == null -> {
                forespoerselDao.lagre(nyForespoersel, nyForespoersel.forespoerselId)
                sendMeldingOmNyForespoersel(nyForespoersel)
            }

            // Siden vi legger til historisk forespurt data når en forespørsel leses fra databasen, så kan innkommende forespørsel anses som duplikat selv om forespurt data er ulik databaseraden til den aktive forespørselen
            !nyForespoersel.erDuplikatAv(aktivForespoersel) -> {
                forespoerselDao.lagre(nyForespoersel, aktivForespoersel.forespoerselId)
                sendMeldingOmOppdatering(nyForespoersel, aktivForespoersel.forespoerselId)
            }

            else -> {
                forespoerselDao.oppdaterForrigeKontaktFraSpleis(aktivForespoersel.forespoerselId)
                loggernaut.info("Lagret ikke duplikatforespørsel.")
            }
        }
    }

    private fun sendMeldingOmNyForespoersel(nyForespoersel: ForespoerselDto) {
        val skalHaPaaminnelse = nyForespoersel.type == Type.KOMPLETT

        val melding =
            arrayOf(
                Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
                Pri.Key.FORESPOERSEL_ID to nyForespoersel.forespoerselId.toJson(),
                Pri.Key.FORESPOERSEL to ForespoerselSimba(nyForespoersel).toJson(ForespoerselSimba.serializer()),
                Pri.Key.SKAL_HA_PAAMINNELSE to skalHaPaaminnelse.toJson(Boolean.serializer()),
            )

        priProducer.send(nyForespoersel.vedtaksperiodeId, *melding)

        loggernaut.info("Sa ifra om mottatt forespørsel til Simba.")
    }

    private fun sendMeldingOmOppdatering(
        nyForespoersel: ForespoerselDto,
        eksponertForespoerselId: UUID,
    ) {
        val melding =
            arrayOf(
                Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_OPPDATERT.toJson(Pri.NotisType.serializer()),
                Pri.Key.EKSPONERT_FORESPOERSEL_ID to eksponertForespoerselId.toJson(),
                Pri.Key.FORESPOERSEL_ID to nyForespoersel.forespoerselId.toJson(),
                Pri.Key.FORESPOERSEL to ForespoerselSimba(nyForespoersel).toJson(ForespoerselSimba.serializer()),
            )

        priProducer.send(nyForespoersel.vedtaksperiodeId, *melding)

        loggernaut.info("Sa ifra om oppdatert forespørsel til LPS-API.")
    }
}
