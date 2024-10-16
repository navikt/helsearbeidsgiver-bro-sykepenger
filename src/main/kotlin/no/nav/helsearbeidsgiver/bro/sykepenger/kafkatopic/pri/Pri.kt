package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri

import kotlinx.serialization.Serializable
import no.nav.helsearbeidsgiver.utils.json.serializer.AsStringSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key as TopicKey

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

    @Serializable(KeySerializer::class)
    enum class Key(
        override val verdi: String,
    ) : TopicKey {
        // Predefinerte fra rapids-and-rivers-biblioteket
        BEHOV("@behov"),
        LØSNING("@løsning"),

        // Egendefinerte
        NOTIS("notis"),
        BOOMERANG("boomerang"),
        ORGNR("orgnr"),
        FNR("fnr"),
        FORESPOERSEL_ID("forespoerselId"),
        SPINN_INNTEKTSMELDING_ID("spinnInntektsmeldingId"),
        VEDTAKSPERIODE_ID_LISTE("vedtaksperiode_id_liste"),
        ;

        override fun toString(): String = verdi

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
        TRENGER_FORESPØRSEL,
        HENT_FORESPOERSLER_FOR_FNR_OG_ORGNR,
        HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE,
    }

    @Serializable
    enum class NotisType {
        FORESPØRSEL_MOTTATT,
        FORESPOERSEL_BESVART,
        FORESPOERSEL_BESVART_SIMBA,
        FORESPOERSEL_FORKASTET,
        FORESPOERSEL_KASTET_TIL_INFOTRYGD,
    }

    private object KeySerializer : AsStringSerializer<Key>(
        serialName = "helsearbeidsgiver.kotlinx.bro.Pri.Key",
        parse = Key::fromJson,
    )
}
