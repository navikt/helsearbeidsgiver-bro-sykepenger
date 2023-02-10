package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = value.toString().let(encoder::encodeString)
    override fun deserialize(decoder: Decoder): LocalDate = decoder.decodeString().let(LocalDate::parse)
}

object YearMonthSerializer : KSerializer<YearMonth> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("java.time.YearMonth", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: YearMonth) = value.toString().let(encoder::encodeString)
    override fun deserialize(decoder: Decoder): YearMonth = decoder.decodeString().let(YearMonth::parse)
}

object UuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("java.util.UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = value.toString().let(encoder::encodeString)
    override fun deserialize(decoder: Decoder): UUID = decoder.decodeString().let(UUID::fromString)
}
