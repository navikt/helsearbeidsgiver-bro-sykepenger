package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.keysAsString
import no.nav.helsearbeidsgiver.utils.json.toJson

fun TestRapid.sendJson(vararg keyValuePairs: Pair<Key, JsonElement?>) {
    keyValuePairs.toMap()
        .mapNotNull { (key, value) -> value?.let { key to it } }
        .toMap()
        .keysAsString()
        .toJson()
        .toString()
        .let(this::sendTestMessage)
}