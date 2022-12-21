package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.Key
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson

private val jsonWhitespaceRegex = Regex("""("(?:\\"|[^"])*")|\s""")

fun String.removeJsonWhitespace(): String =
    replace(jsonWhitespaceRegex, "$1")

fun TestRapid.sendJson(vararg keyValuePairs: Pair<Key, JsonElement>) {
    keyValuePairs.toMap()
        .mapKeys { (key, _) -> key.str }
        .let(this::sendJson)
}

fun TestRapid.sendJson(keyValuePairs: Map<String, JsonElement>) {
    keyValuePairs.toJson()
        .toString()
        .let(this::sendTestMessage)
}
