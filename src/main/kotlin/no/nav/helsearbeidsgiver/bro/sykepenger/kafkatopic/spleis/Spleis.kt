package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key as TopicKey

object Spleis {
    @Serializable(KeySerializer::class)
    enum class Key : TopicKey {
        // Egendefinerte
        TYPE,
        ORGANISASJONSNUMMER,
        FØDSELSNUMMER,
        VEDTAKSPERIODE_ID,
        BESTEMMENDE_FRAVÆRSDAGER,
        SYKMELDINGSPERIODER,
        EGENMELDINGSPERIODER,
        FOM,
        TOM,
        FORESPURT_DATA,
        DOKUMENT_ID,
        OPPRETTET,
        VEDTAKSPERIODE_IDER_MED_SAMME_FRAVAERSDAG,
        ;

        override fun toString(): String =
            when (this) {
                BESTEMMENDE_FRAVÆRSDAGER -> "bestemmendeFraværsdager"
                DOKUMENT_ID -> "dokumentId"
                FORESPURT_DATA -> "forespurtData"
                OPPRETTET -> "@opprettet"
                VEDTAKSPERIODE_ID -> "vedtaksperiodeId"
                VEDTAKSPERIODE_IDER_MED_SAMME_FRAVAERSDAG -> "vedtaksperioderMedSammeFørsteFraværsdag"
                else -> name.lowercase()
            }

        companion object {
            fun fromJson(key: String): Key =
                entries.firstOrNull {
                    key == it.toString()
                }
                    ?: throw IllegalArgumentException("Fant ingen Spleis.Key med verdi som samsvarte med '$key'.")
        }
    }

    @Serializable
    enum class Event {
        TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_KOMPLETT,
        TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER_BEGRENSET,
        TRENGER_IKKE_OPPLYSNINGER_FRA_ARBEIDSGIVER,
        INNTEKTSMELDING_HÅNDTERT,
        VEDTAKSPERIODE_FORKASTET,
    }

    private object KeySerializer : AsStringSerializer<Key>(
        serialName = "helsearbeidsgiver.kotlinx.bro.Spleis.Key",
        parse = Key::fromJson,
    )
}
