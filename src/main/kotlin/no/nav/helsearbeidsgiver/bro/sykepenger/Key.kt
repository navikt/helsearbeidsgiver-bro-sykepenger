package no.nav.helsearbeidsgiver.bro.sykepenger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage

enum class Key(val str: String) {
    // Egendefinerte
    TYPE("type"),
    ORGANISASJONSNUMMER("organisasjonsnummer"),
    FØDSELSNUMMER("fødselsnummer"),
    VEDTAKSPERIODE_ID("vedtaksperiodeId"),
    FOM("fom"),
    TOM("tom"),
    FORESPURT_DATA("forespurtData");

    override fun toString(): String =
        str
}

fun JsonMessage.value(key: Key): JsonNode =
    this[key.str]
