package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage

fun JsonMessage.require(vararg keyParserPairs: Pair<String, (JsonNode) -> Any>) {
    keyParserPairs.forEach { (key, parser) ->
        this.require(key, parser)
    }
}
