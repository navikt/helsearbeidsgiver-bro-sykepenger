package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.utils.json.parseJson

fun JsonNode.toJsonElement(): JsonElement =
    toString().parseJson()
