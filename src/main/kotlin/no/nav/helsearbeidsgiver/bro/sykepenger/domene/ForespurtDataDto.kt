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
import java.time.YearMonth

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("opplysningstype")
sealed class ForespurtDataDto

@Serializable
@SerialName("Arbeidsgiverperiode")
object ArbeidsgiverPeriode : ForespurtDataDto()

@Serializable
@SerialName("Inntekt")
data class Inntekt(val forslag: ForslagInntekt) : ForespurtDataDto()

@Serializable
@SerialName("FastsattInntekt")
data class FastsattInntekt(val fastsattInntekt: Double) : ForespurtDataDto()

@Serializable
@SerialName("Refusjon")
data class Refusjon(val forslag: ForslagRefusjon) : ForespurtDataDto()

@Serializable
@SerialName("SpleisRefusjon")
data class SpleisRefusjon(val forslag: List<SpleisForslagRefusjon>) : ForespurtDataDto()

@Serializable
data class ForslagInntekt(
    val beregningsmåneder: List<YearMonth>
)

@Serializable
data class ForslagRefusjon(
    val perioder: List<RefusjonPeriode>,
    val opphoersdato: LocalDate?
)

@Serializable
data class RefusjonPeriode(
    val fom: LocalDate,
    val beloep: Double
)

@Serializable
data class SpleisForslagRefusjon(
    val fom: LocalDate,
    val tom: LocalDate?,
    val beløp: Double
)
