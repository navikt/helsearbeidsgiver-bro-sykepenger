package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.Key

private val jsonWhitespaceRegex = Regex("""("(?:\\"|[^"])*")|\s""")

fun String.removeJsonWhitespace(): String =
    replace(jsonWhitespaceRegex, "$1")

fun TestRapid.sendJson(vararg keyValuePairs: Pair<Key, JsonElement>) {
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .tryToJson()
        .toString()
        .let(this::sendTestMessage)
}

/** Obs! Denne kan feile runtime. */
inline fun <reified T : Any> T.tryToJson(): JsonElement =
    Json.encodeToJsonElement(this)
