package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJsonElementOrNull
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key as TopicKey

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

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

        override fun fra(message: JsonMessage): JsonElement =
            message[verdi].toJsonElement()

        override fun fraEllerNull(message: JsonMessage): JsonElement? =
            message[verdi].toJsonElementOrNull()
    }

    @Serializable
    enum class BehovType {
        TRENGER_FORESPØRSEL
    }

    @Serializable
    enum class NotisType {
        FORESPØRSEL_MOTTATT
    }
}
