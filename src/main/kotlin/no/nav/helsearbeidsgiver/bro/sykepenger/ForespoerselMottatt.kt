package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val jsonBuilder = Json {
    encodeDefaults = true
}

@Serializable
data class ForespoerselMottatt(
    val orgnr: String,
    val fnr: String
) {
    val eventType = "FORESPØRSEL_MOTTATT"

    fun toJson(): String =
        jsonBuilder.encodeToString(this)
}
