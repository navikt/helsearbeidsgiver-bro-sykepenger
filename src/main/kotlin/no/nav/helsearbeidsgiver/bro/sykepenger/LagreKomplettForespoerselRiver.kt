package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.demandValues
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.require
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.requireKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list

class LagreKomplettForespoerselRiver(
    rapid: RapidsConnection,
    forespoerselDao: ForespoerselDao,
    priProducer: PriProducer
) : LagreForespoerselRiver(forespoerselDao, priProducer) {
    override val loggernaut = Loggernaut(this)

    init {
        River(rapid).apply {
            validate { msg ->
                msg.demandValues(Spleis.Key.TYPE to Spleis.Event.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_KOMPLETT.name)
                msg.requireArray(Spleis.Key.SYKMELDINGSPERIODER.verdi) {
                    require(
                        Spleis.Key.FOM to { it.fromJson(LocalDateSerializer) },
                        Spleis.Key.TOM to { it.fromJson(LocalDateSerializer) }
                    )
                }
                msg.requireKeys(
                    Spleis.Key.ORGANISASJONSNUMMER,
                    Spleis.Key.FØDSELSNUMMER,
                    Spleis.Key.VEDTAKSPERIODE_ID,
                    Spleis.Key.SKJÆRINGSTIDSPUNKT,
                    Spleis.Key.FORESPURT_DATA
                )
            }
        }.register(this)
    }

    override fun lesForespoersel(packet: JsonMessage): ForespoerselDto =
        ForespoerselDto(
            forespoerselId = randomUuid(),
            orgnr = Spleis.Key.ORGANISASJONSNUMMER.fra(packet).fromJson(Orgnr.serializer()),
            fnr = Spleis.Key.FØDSELSNUMMER.fra(packet).fromJson(String.serializer()),
            vedtaksperiodeId = Spleis.Key.VEDTAKSPERIODE_ID.fra(packet).fromJson(UuidSerializer),
            skjaeringstidspunkt = Spleis.Key.SKJÆRINGSTIDSPUNKT.fra(packet).fromJson(LocalDateSerializer),
            sykmeldingsperioder = Spleis.Key.SYKMELDINGSPERIODER.fra(packet).fromJson(Periode.serializer().list()),
            forespurtData = Spleis.Key.FORESPURT_DATA.fra(packet).fromJson(ForespurtDataDto.serializer().list()),
            forespoerselBesvart = null,
            status = Status.AKTIV,
            type = Type.KOMPLETT
        )
}
