package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson

fun JsonNode.toJsonElement(): JsonElement =
    toString().parseJson()

fun JsonNode.toJsonElementOrNull(): JsonElement? =
    if (isMissingOrNull()) {
        null
    } else {
        toJsonElement()
    }

fun <K : Key, T : Any> K.lesOrNull(serializer: KSerializer<T>, melding: Map<K, JsonElement>): T? =
    melding[this]?.fromJson(serializer)

fun <K : Key, T : Any> K.les(serializer: KSerializer<T>, melding: Map<K, JsonElement>): T =
    lesOrNull(serializer, melding)
        ?: throw IllegalArgumentException("Felt '$this' mangler i JSON-map.")
