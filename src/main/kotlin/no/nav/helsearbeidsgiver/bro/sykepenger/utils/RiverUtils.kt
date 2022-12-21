package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import java.util.UUID

fun JsonMessage.require(vararg keyParserPairs: Pair<String, (JsonNode) -> Any>) {
    keyParserPairs.forEach { (key, parser) ->
        this.require(key, parser)
    }
}

fun JsonNode.asUuid(): UUID =
    asText().let(UUID::fromString)
