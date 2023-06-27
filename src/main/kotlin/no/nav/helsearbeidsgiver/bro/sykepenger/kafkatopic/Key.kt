package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage

interface Key {
    val verdi: String

    fun fra(message: JsonMessage): JsonElement

    fun fraEllerNull(message: JsonMessage): JsonElement?
}

fun <K : Key, V : Any> Map<K, V>.keysAsString(): Map<String, V> =
    mapKeys { (key, _) -> key.verdi }
