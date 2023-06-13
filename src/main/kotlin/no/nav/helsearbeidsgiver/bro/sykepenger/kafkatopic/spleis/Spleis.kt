package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key as TopicKey

object Spleis {
    enum class Key(override val verdi: String) : TopicKey {
        // Egendefinerte
        TYPE("type"),
        ORGANISASJONSNUMMER("organisasjonsnummer"),
        FØDSELSNUMMER("fødselsnummer"),
        VEDTAKSPERIODE_ID("vedtaksperiodeId"),
        SKJÆRINGSTIDSPUNKT("skjæringstidspunkt"),
        SYKMELDINGSPERIODER("sykmeldingsperioder"),
        EGENMELDINGSPERIODER("egenmeldingsperioder"),
        FOM("fom"),
        TOM("tom"),
        FORESPURT_DATA("forespurtData");

        override fun toString(): String =
            verdi

        override fun fra(message: JsonMessage): JsonElement =
            message[verdi].toJsonElement()
    }

    @Serializable
    enum class Event {
        TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER,
        TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_KOMPLETT
    }
}
