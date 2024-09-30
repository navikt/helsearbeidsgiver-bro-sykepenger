package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key
import no.nav.helsearbeidsgiver.utils.json.fromJson

fun <K : Key, T : Any> K.lesOrNull(
    serializer: KSerializer<T>,
    melding: Map<K, JsonElement>,
): T? = melding[this]?.fromJson(serializer.nullable)

fun <K : Key, T : Any> K.les(
    serializer: KSerializer<T>,
    melding: Map<K, JsonElement>,
): T =
    lesOrNull(serializer, melding)
        ?: throw IllegalArgumentException("Felt '$this' mangler i JSON-map.")
