package no.nav.helsearbeidsgiver.bro.sykepenger.pritopic

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson

fun JsonMessage.value(key: Pri.Key): JsonNode =
    this[key.str]

fun jsonOf(vararg keyValuePairs: Pair<Pri.Key, JsonElement>): JsonElement =
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .toJson()
