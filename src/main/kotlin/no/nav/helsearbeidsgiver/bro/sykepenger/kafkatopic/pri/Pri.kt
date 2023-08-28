package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key as TopicKey

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

    @Serializable(KeySerializer::class)
    enum class Key(override val verdi: String) : TopicKey {
        // Predefinerte fra rapids-and-rivers-biblioteket
        BEHOV("@behov"),
        LØSNING("@løsning"),

        // Egendefinerte
        NOTIS("notis"),
        BOOMERANG("boomerang"),
        ORGNR("orgnr"),
        FNR("fnr"),
        FORESPOERSEL_ID("forespoerselId");

        override fun toString(): String =
            verdi

        companion object {
            fun fromJson(json: String): Key =
                Key.entries.firstOrNull {
                    json == it.verdi
                }
                    ?: throw IllegalArgumentException("Fant ingen Pri.Key med verdi som matchet '$json'.")
        }
    }

    @Serializable
    enum class BehovType {
        TRENGER_FORESPØRSEL
    }

    @Serializable
    enum class NotisType {
        FORESPØRSEL_MOTTATT,
        FORESPOERSEL_BESVART,
        FORESPOERSEL_FORKASTET
    }

    private object KeySerializer : AsStringSerializer<Key>(
        serialName = "helsearbeidsgiver.kotlinx.bro.Pri.Key",
        parse = Key::fromJson
    )
}
