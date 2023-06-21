package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.rejectKeys
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.require
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.time.LocalDate
import java.util.UUID

/* Tilgjengeliggjør hvilke data spleis forespør fra arbeidsgiver */
class TilgjengeliggjoerForespoerselRiver(
    rapid: RapidsConnection,
    private val forespoerselDao: ForespoerselDao,
    private val priProducer: PriProducer
) : River.PacketListener {
    private val loggernaut = Loggernaut(this)

    init {
        River(rapid).apply {
            validate { msg ->
                msg.demandValues(
                    Pri.Key.BEHOV to ForespoerselSvar.behovType.name
                )
                msg.require(
                    Pri.Key.FORESPOERSEL_ID to { it.fromJson(UuidSerializer) }
                )
                msg.requireKeys(Pri.Key.BOOMERANG)
                msg.rejectKeys(Pri.Key.LØSNING)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val forespoerselId = Pri.Key.FORESPOERSEL_ID.fra(packet).fromJson(UuidSerializer)

        MdcUtils.withLogFields(
            "forespoerselId" to forespoerselId.toString()
        ) {
            runCatching {
                packet.loesBehov(forespoerselId)
            }
                .onFailure(loggernaut::ukjentFeil)
        }
    }

    fun JsonMessage.loesBehov(forespoerselId: UUID) {
        loggernaut.aapen.info("Mottok melding på pri-topic av type '${Pri.Key.BEHOV.fra(this).fromJson(String.serializer())}'.")
        loggernaut.sikker.info("Mottok melding på pri-topic med innhold:\n${toJson()}")

        val forespoerselSvar = ForespoerselSvar(
            forespoerselId = forespoerselId,
            boomerang = Pri.Key.BOOMERANG.fra(this)
        )
            .let {
                val forespoersel = forespoerselDao.hentAktivForespoerselFor(it.forespoerselId)
                    ?.medEksplisitteRefusjonsforslag()

                if (forespoersel != null) {
                    loggernaut.aapen.info("Forespørsel funnet.")
                    it.copy(resultat = ForespoerselSvar.Suksess(forespoersel))
                } else {
                    loggernaut.aapen.info("Forespørsel _ikke_ funnet.")
                    it.copy(feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET)
                }
            }

        priProducer.send(
            Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to forespoerselSvar.toJson(ForespoerselSvar.serializer())
        )

        loggernaut.aapen.info("Behov besvart på pri-topic med forespørsel.")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggernaut.innkommendeMeldingFeil(problems)
    }

    private fun ForespoerselDto.medEksplisitteRefusjonsforslag(): ForespoerselDto {
        val nyForespoersel = forespurtData.map { forespurtElement ->
            if (forespurtElement is Refusjon) {
                forespurtElement.forslag
                    .sortedBy { it.fom }
                    .medEksplisitteTilDatoer()
                    .medEksplisitteRefusjonsopphold()
                    .let(::Refusjon)
            } else {
                forespurtElement
            }
        }
            .let {
                copy(forespurtData = it)
            }

        if (refusjonsforslag().isNotEmpty() && refusjonsforslag().size != nyForespoersel.refusjonsforslag().size) {
            loggernaut.sikker.info("Refusjonsforslag endret fra ${refusjonsforslag()} til ${nyForespoersel.refusjonsforslag()}.")
        }

        return nyForespoersel
    }
}

private fun List<ForslagRefusjon>.medEksplisitteTilDatoer(): List<ForslagRefusjon> =
    mapWithNext { current, next ->
        if (current.tom != null || next == null) {
            current
        } else {
            current.copy(
                tom = next.fom.minusDays(1)
            )
        }
    }

private fun List<ForslagRefusjon>.medEksplisitteRefusjonsopphold(): List<ForslagRefusjon> =
    mapWithNext { current, next ->
        if (current.tom == null || next == null || current.tom.isDayBefore(next.fom)) {
            listOf(current)
        } else {
            listOf(
                current,
                ForslagRefusjon(
                    fom = current.tom.plusDays(1),
                    tom = next.fom.minusDays(1),
                    beløp = 0.0
                )
            )
        }
    }
        .flatten()

private fun LocalDate.isDayBefore(other: LocalDate): Boolean =
    this == other.minusDays(1)

private fun ForespoerselDto.refusjonsforslag(): List<ForslagRefusjon> =
    forespurtData.filterIsInstance<Refusjon>().firstOrNull()?.forslag.orEmpty()

private fun <T : Any, R : Any> List<T>.mapWithNext(transform: (T, T?) -> R): List<R> =
    windowed(size = 2, partialWindows = true)
        .map {
            val current = it[0]
            val next = it.getOrNull(1)

            transform(current, next)
        }
