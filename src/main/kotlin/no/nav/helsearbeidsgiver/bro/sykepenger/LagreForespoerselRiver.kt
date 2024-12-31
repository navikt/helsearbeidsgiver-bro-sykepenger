package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
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
import no.nav.helsearbeidsgiver.utils.pipe.ifFalse
import no.nav.helsearbeidsgiver.utils.pipe.ifTrue
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
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
                    loggernaut.aapen.error("Klarte ikke å lagre forespørsel!")
                    loggernaut.sikker.error("Klarte ikke å lagre forespørsel!", it)
                }
        }
    }

    private fun JsonElement.lagreForespoersel(forespoerselId: UUID) {
        val melding = fromJsonMapFiltered(Spleis.Key.serializer())

        loggernaut.aapen.info("Mottok melding av type '${Spleis.Key.TYPE.les(String.serializer(), melding)}'")
        loggernaut.sikker.info("Mottok melding med innhold:\n${toPretty()}")

        val nyForespoersel = lesForespoersel(forespoerselId, melding)
        loggernaut.sikker.info("Forespoersel lest: $nyForespoersel")

        MdcUtils.withLogFields(
            Log.type(nyForespoersel.type),
            Log.vedtaksperiodeId(nyForespoersel.vedtaksperiodeId),
        ) {
            lagreForespoersel(nyForespoersel)
        }
    }

    private fun lagreForespoersel(nyForespoersel: ForespoerselDto) {
        val aktivForespoersel =
            forespoerselDao.hentAktivForespoerselForVedtaksperiodeId(nyForespoersel.vedtaksperiodeId)

        val skalHaPaaminnelse = nyForespoersel.type == Type.KOMPLETT

        if (aktivForespoersel == null || !nyForespoersel.erDuplikatAv(aktivForespoersel)) {
            forespoerselDao
                .lagre(nyForespoersel)
                .let { id ->
                    "Forespørsel lagret med id=$id.".also {
                        loggernaut.aapen.info(it)
                        loggernaut.sikker.info(it)
                    }
                }
        } else {
            "Lagret ikke duplikatforespørsel.".also {
                loggernaut.aapen.info(it)
                loggernaut.sikker.info(it)
            }
        }

        // Ikke send notis ved oppdatering av forespørsel som er ubesvart
        if (aktivForespoersel == null) {
            priProducer
                .send(
                    Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
                    Pri.Key.FORESPOERSEL_ID to nyForespoersel.forespoerselId.toJson(),
                    Pri.Key.ORGNR to nyForespoersel.orgnr.toJson(Orgnr.serializer()),
                    Pri.Key.FNR to nyForespoersel.fnr.toJson(Fnr.serializer()),
                    Pri.Key.SKAL_HA_PAAMINNELSE to skalHaPaaminnelse.toJson(Boolean.serializer()),
                    Pri.Key.FORESPOERSEL to ForespoerselSimba(nyForespoersel).toJson(ForespoerselSimba.serializer()),
                ).ifTrue { loggernaut.aapen.info("Sa ifra om mottatt forespørsel til Simba.") }
                .ifFalse { loggernaut.aapen.error("Klarte ikke si ifra om mottatt forespørsel til Simba.") }

            val besvarteForespoersler =
                forespoerselDao.hentForespoerslerForVedtaksperiodeId(
                    nyForespoersel.vedtaksperiodeId,
                    setOf(Status.BESVART_SIMBA, Status.BESVART_SPLEIS),
                )
            if (besvarteForespoersler.size > 3) {
                loggernaut.warn(
                    "Ny IM har nettopp blitt etterspurt for vedtaksperiode-ID ${nyForespoersel.vedtaksperiodeId}, " +
                        "som allerede har blitt besvart mer enn 3 ganger.",
                )
            }
        } else {
            loggernaut.aapen.info("Sa ikke ifra om mottatt forespørsel til Simba fordi det er en oppdatering av eksisterende forespørsel.")
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
    ) {
        loggernaut.innkommendeMeldingFeil(problems)
    }
}
