package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

private val jsonBuilder = jsonBuilderWithDefaults()

@Serializable
data class ForespoerselMottatt(
    val orgnr: String,
    val fnr: String,
    @Serializable(with = UUIDSerializer::class)
    val vedtaksperiodeId: UUID
) {
    val eventType = "FORESPØRSEL_MOTTATT"

    fun toJson(): JsonElement =
        jsonBuilder.encodeToJsonElement(this)
}
