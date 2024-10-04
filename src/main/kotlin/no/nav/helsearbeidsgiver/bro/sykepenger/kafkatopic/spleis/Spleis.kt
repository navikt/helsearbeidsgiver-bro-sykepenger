package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key as TopicKey

object Spleis {
    @Serializable(KeySerializer::class)
    enum class Key(
        override val verdi: String,
    ) : TopicKey {
        // Egendefinerte
        TYPE("type"),
        ORGANISASJONSNUMMER("organisasjonsnummer"),
        FØDSELSNUMMER("fødselsnummer"),
        VEDTAKSPERIODE_ID("vedtaksperiodeId"),
        BESTEMMENDE_FRAVÆRSDAGER("bestemmendeFraværsdager"),
        SYKMELDINGSPERIODER("sykmeldingsperioder"),
        EGENMELDINGSPERIODER("egenmeldingsperioder"),
        FOM("fom"),
        TOM("tom"),
        FORESPURT_DATA("forespurtData"),
        DOKUMENT_ID("dokumentId"),
        OPPRETTET("@opprettet"),
        ;

        override fun toString(): String = verdi

        companion object {
            fun fromJson(json: String): Key =
                Key.entries.firstOrNull {
                    json == it.verdi
                }
                    ?: throw IllegalArgumentException("Fant ingen Spleis.Key med verdi som matchet '$json'.")
        }
    }

    @Serializable
    enum class Event {
        TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_KOMPLETT,
        TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_BEGRENSET,
        TRENGER_IKKE_OPPLYSNINGER_FRA_ARBEIDSGIVER,
        INNTEKTSMELDING_HÅNDTERT,
    }

    private object KeySerializer : AsStringSerializer<Key>(
        serialName = "helsearbeidsgiver.kotlinx.bro.Spleis.Key",
        parse = Key::fromJson,
    )
}
