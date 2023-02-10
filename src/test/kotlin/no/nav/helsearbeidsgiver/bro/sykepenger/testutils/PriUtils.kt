package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.keysAsString

fun TestRapid.sendJson(vararg keyValuePairs: Pair<Pri.Key, JsonElement>) {
    keyValuePairs.toMap()
        .keysAsString()
        .let(this::sendJson)
}
