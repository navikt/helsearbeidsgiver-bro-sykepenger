package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key as TopicKey

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

    @Serializable(KeySerializer::class)
    enum class Key : TopicKey {
        // Predefinerte fra rapids-and-rivers-biblioteket
        BEHOV,
        LØSNING,

        // Egendefinerte
        NOTIS,
        BOOMERANG,
        FNR,
        FORESPOERSEL,
        FORESPOERSEL_LISTE,
        FORESPOERSEL_ID,
        EKSPONERT_FORESPOERSEL_ID,
        VEDTAKSPERIODE_ID,
        VEDTAKSPERIODE_ID_LISTE,
        STATUS,
        SENDT_TID, // TODO: Kanskje ikke denne er så viktig? Gjør testing litt kålete (må bruke mockStatic)
        ;

        override fun toString(): String =
            when (this) {
                BEHOV -> "@behov"
                LØSNING -> "@løsning"
                FORESPOERSEL_ID -> "forespoerselId"
                EKSPONERT_FORESPOERSEL_ID -> "eksponertForespoerselId"
                else -> name.lowercase()
            }

        companion object {
            fun fromJson(key: String): Key =
                entries.firstOrNull {
                    key == it.toString()
                }
                    ?: throw IllegalArgumentException("Fant ingen Pri.Key med verdi som samsvarte med '$key'.")
        }
    }

    @Serializable
    enum class BehovType {
        TRENGER_FORESPØRSEL,
        HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE,
        HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID,
        HENT_FORESPOERSLER_FOR_PERSON,
    }

    @Serializable
    enum class NotisType {
        FORESPØRSEL_MOTTATT,
        FORESPOERSEL_BESVART,
        FORESPOERSEL_BESVART_SIMBA,
        FORESPOERSEL_FORKASTET,
        FORESPOERSEL_OPPDATERT,
        FORESPOERSEL_KASTET_TIL_INFOTRYGD,
        FORESPOERSEL_MANUELT_FORKASTET, // Sendes fra HAG-Admin
        FORESPOERSEL_FOR_VEDTAKSPERIODE_ID,
        FORESPOERSEL_LISTE_FOR_PERSON,
    }

    private object KeySerializer : AsStringSerializer<Key>(
        serialName = "helsearbeidsgiver.kotlinx.bro.Pri.Key",
        parse = Key::fromJson,
    )
}
