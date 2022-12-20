@file:UseSerializers(UUIDSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

private val jsonBuilder = jsonBuilderWithDefaults()

@Serializable
data class ForespoerselMottatt(
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID
) {
    val notis = Event.FORESPOERSEL_MOTTATT

    fun toJson(): JsonElement =
        jsonBuilder.encodeToJsonElement(this)
}
