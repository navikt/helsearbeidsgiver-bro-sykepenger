package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate
import java.util.UUID

val jsonIgnoreUnknown = Json {
    ignoreUnknownKeys = true
}

fun <T : Any> T.toJson(serializer: KSerializer<T>): JsonElement =
    Json.encodeToJsonElement(serializer, this)

fun <T : Any> T.toJsonStr(serializer: KSerializer<T>): String =
    toJson(serializer).toString()

fun <T : Any> List<T>.toJson(elementSerializer: KSerializer<T>): JsonElement =
    toJson(
        elementSerializer.list()
    )

fun String.toJson(): JsonElement =
    toJson(String.serializer())

fun LocalDate.toJson(): JsonElement =
    toJson(LocalDateSerializer)

fun UUID.toJson(): JsonElement =
    toJson(UuidSerializer)

fun Map<String, JsonElement>.toJson(): JsonElement =
    toJson(
        MapSerializer(
            String.serializer(),
            JsonElement.serializer()
        )
    )

fun <T : Any> JsonElement.fromJson(serializer: KSerializer<T>): T =
    jsonIgnoreUnknown.decodeFromJsonElement(serializer, this)

fun String.parseJson(): JsonElement =
    Json.parseToJsonElement(this)

fun JsonNode.toJsonElement(): JsonElement =
    toString().parseJson()

fun <T : Any> KSerializer<T>.list(): KSerializer<List<T>> =
    ListSerializer(this)
