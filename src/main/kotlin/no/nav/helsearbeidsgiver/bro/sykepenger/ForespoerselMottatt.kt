package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

private val jsonBuilder = Json {
    encodeDefaults = true
}

@Serializable
data class ForespoerselMottatt(
    val orgnr: String,
    val fnr: String,
    @Serializable(with = UUIDSerializer::class)
    val vedtaksperiodeId: UUID
) {
    val eventType = "FORESPØRSEL_MOTTATT"

    fun toJson(): String =
        jsonBuilder.encodeToString(this)
}
