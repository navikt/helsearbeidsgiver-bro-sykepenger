package no.nav.helsearbeidsgiver.bro.sykepenger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage

enum class Key(val str: String) {
    EVENT_TYPE("eventType"),
    TYPE("type"),
    ORGANISASJONSNUMMER("organisasjonsnummer"),
    ORGNR("orgnr"),
    FØDSELSNUMMER("fødselsnummer"),
    FNR("fnr"),
    VEDTAKSPERIODE_ID("vedtaksperiodeId"),
    FORESPURT_DATA("forespurtData"),
    FOM("fom"),
    TOM("tom")
}

fun JsonMessage.value(key: Key): JsonNode =
    this[key.str]
