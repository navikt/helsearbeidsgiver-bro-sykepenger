package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun sikkerLogger(): Logger =
    LoggerFactory.getLogger("tjenestekall")

fun jsonBuilderWithDefaults() =
    Json {
        encodeDefaults = true
    }
