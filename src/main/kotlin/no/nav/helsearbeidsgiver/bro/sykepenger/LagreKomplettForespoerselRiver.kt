package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.db.bestemmendeFravaersdagerSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.les
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.require
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import java.util.UUID

class LagreKomplettForespoerselRiver(
    rapid: RapidsConnection,
    forespoerselDao: ForespoerselDao,
    priProducer: PriProducer,
) : LagreForespoerselRiver(forespoerselDao, priProducer) {
    override val loggernaut = Loggernaut(this)

    init {
        River(rapid).apply {
            validate { msg ->
                msg.demandValues(Spleis.Key.TYPE to Spleis.Event.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_KOMPLETT.name)
                msg.requireArray(Spleis.Key.EGENMELDINGSPERIODER.verdi) {
                    require(
                        Spleis.Key.FOM to { it.fromJson(LocalDateSerializer) },
                        Spleis.Key.TOM to { it.fromJson(LocalDateSerializer) },
                    )
                }
                msg.requireArray(Spleis.Key.SYKMELDINGSPERIODER.verdi) {
                    require(
                        Spleis.Key.FOM to { it.fromJson(LocalDateSerializer) },
                        Spleis.Key.TOM to { it.fromJson(LocalDateSerializer) },
                    )
                }
                msg.requireKeys(
                    Spleis.Key.ORGANISASJONSNUMMER,
                    Spleis.Key.FØDSELSNUMMER,
                    Spleis.Key.VEDTAKSPERIODE_ID,
                    Spleis.Key.BESTEMMENDE_FRAVÆRSDAGER,
                    Spleis.Key.FORESPURT_DATA,
                )
            }
        }.register(this)
    }

    override fun lesForespoersel(
        forespoerselId: UUID,
        melding: Map<Spleis.Key, JsonElement>,
    ): ForespoerselDto {
        val orgnr = Spleis.Key.ORGANISASJONSNUMMER.les(Orgnr.serializer(), melding)
        val bestemmendeFravaersdager =
            Spleis.Key.BESTEMMENDE_FRAVÆRSDAGER.les(bestemmendeFravaersdagerSerializer, melding)

        return ForespoerselDto(
            forespoerselId = forespoerselId,
            type = Type.KOMPLETT,
            status = Status.AKTIV,
            orgnr = orgnr,
            fnr = Spleis.Key.FØDSELSNUMMER.les(String.serializer(), melding),
            vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.les(UuidSerializer, melding),
            egenmeldingsperioder = Spleis.Key.EGENMELDINGSPERIODER.les(Periode.serializer().list(), melding),
            sykmeldingsperioder = Spleis.Key.SYKMELDINGSPERIODER.les(Periode.serializer().list(), melding),
            skjaeringstidspunkt = null,
            bestemmendeFravaersdager = bestemmendeFravaersdager,
            forespurtData = Spleis.Key.FORESPURT_DATA.les(SpleisForespurtDataDto.serializer().list(), melding),
            besvarelse = null,
        )
    }
}
