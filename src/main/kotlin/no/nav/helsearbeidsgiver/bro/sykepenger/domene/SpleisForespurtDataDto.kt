@file:UseSerializers(LocalDateSerializer::class, YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import java.time.LocalDate

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("opplysningstype")
sealed class SpleisForespurtDataDto

@Serializable
@SerialName("Arbeidsgiverperiode")
data object SpleisArbeidsgiverperiode : SpleisForespurtDataDto()

@Serializable
@SerialName("Inntekt")
data class SpleisInntekt(
    val forslag: SpleisForslagInntekt,
) : SpleisForespurtDataDto()

@Serializable
@SerialName("FastsattInntekt")
data class SpleisFastsattInntekt(
    val fastsattInntekt: Double,
) : SpleisForespurtDataDto()

@Serializable
@SerialName("Refusjon")
data class SpleisRefusjon(
    val forslag: List<SpleisForslagRefusjon>,
) : SpleisForespurtDataDto()

@Serializable
data class SpleisForslagInntekt(
    val forrigeInntekt: SpleisForrigeInntekt? = null,
)

@Serializable
data class SpleisForslagRefusjon(
    val fom: LocalDate,
    val tom: LocalDate?,
    val beløp: Double,
)

@Serializable
data class SpleisForrigeInntekt(
    val skjæringstidspunkt: LocalDate,
    val kilde: String,
    val beløp: Double,
)
