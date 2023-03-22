package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key

fun JsonMessage.demandValues(vararg keyAndValuePairs: Pair<Key, String>) {
    keyAndValuePairs.map { it.mapFirst(Key::verdi) }
        .forEach { (key, value) ->
            demandValue(key, value)
        }
}

fun JsonMessage.rejectKeys(vararg keys: Key) {
    val keysAsStr = keys.map(Key::verdi).toTypedArray()
    rejectKey(*keysAsStr)
}

fun JsonMessage.requireKeys(vararg keys: Key) {
    val keysAsStr = keys.map(Key::verdi).toTypedArray()
    requireKey(*keysAsStr)
}

fun JsonMessage.require(vararg keyAndParserPairs: Pair<Key, (JsonElement) -> Any>) {
    val keyStringAndParserPairs = keyAndParserPairs.map { it.mapFirst(Key::verdi) }
    validate(JsonMessage::require, keyStringAndParserPairs)
}

private fun JsonMessage.validate(
    validateFn: (JsonMessage, String, (JsonNode) -> Any) -> Unit,
    keyAndParserPairs: List<Pair<String, (JsonElement) -> Any>>
) {
    keyAndParserPairs.forEach { (key, block) ->
        validateFn(this, key) {
            it.toJsonElement().let(block)
        }
    }
}

private fun <A, B, X> Pair<A, B>.mapFirst(mapFn: (A) -> X): Pair<X, B> =
    Pair(
        first = mapFn(first),
        second = second
    )
