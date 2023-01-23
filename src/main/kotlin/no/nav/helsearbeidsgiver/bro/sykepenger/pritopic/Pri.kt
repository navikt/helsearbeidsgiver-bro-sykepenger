package no.nav.helsearbeidsgiver.bro.sykepenger.pritopic

object Pri {
    const val TOPIC = "helsearbeidsgiver.pri"

    enum class Key(val str: String) {
        // Predefinerte fra rapids-and-rivers-biblioteket
        BEHOV("@behov"),
        LØSNING("@løsning"),

        // Egendefinerte
        NOTIS("notis"),
        BOOMERANG("boomerang"),
        ORGNR("orgnr"),
        FNR("fnr"),
        FORESPOERSEL_ID("forespoerselId"),
        SYKMELDINGSPERIODER("sykmeldingsperioder"),
        FORESPURT_DATA("forespurtData");

        override fun toString(): String =
            str
    }

    enum class BehovType {
        TRENGER_FORESPØRSEL
    }

    enum class NotisType {
        FORESPØRSEL_MOTTATT
    }
}
