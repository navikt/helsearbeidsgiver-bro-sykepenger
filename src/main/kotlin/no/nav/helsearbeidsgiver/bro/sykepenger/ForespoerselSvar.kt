@file:UseSerializers(UUIDSerializer::class, LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.LocalDate
import java.util.UUID

private val jsonBuilder = jsonBuilderWithDefaults()

@Serializable
data class ForespoerselSvar(
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val forespurtData: List<ForespurtDataDto>
) {
    val eventType = Event.FORESPOERSEL_SVAR

    constructor(forespoersel: ForespoerselDto) : this(
        orgnr = forespoersel.orgnr,
        fnr = forespoersel.fnr,
        vedtaksperiodeId = forespoersel.vedtaksperiodeId,
        fom = forespoersel.fom,
        tom = forespoersel.tom,
        forespurtData = forespoersel.forespurtData
    )

    fun toJson(): JsonElement =
        jsonBuilder.encodeToJsonElement(this)
}
