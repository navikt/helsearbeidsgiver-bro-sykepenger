package no.nav.helsearbeidsgiver.bro.sykepenger.pritopic

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage

fun JsonMessage.value(key: Pri.Key): JsonNode =
    this[key.str]

fun <T : Any> Map<Pri.Key, T>.keysAsString(): Map<String, T> =
    mapKeys { (key, _) -> key.str }
