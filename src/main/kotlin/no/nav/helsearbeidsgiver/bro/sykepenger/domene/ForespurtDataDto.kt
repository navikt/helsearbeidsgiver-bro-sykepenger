@file:UseSerializers(YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import no.nav.helsearbeidsgiver.bro.sykepenger.YearMonthSerializer
import java.time.YearMonth

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("opplysningstype")
sealed class ForespurtDataDto

@Serializable
@SerialName("Arbeidsgiverperiode")
data class ArbeidsgiverPeriode(val forslag: List<Periode>) : ForespurtDataDto()

@Serializable
@SerialName("Inntekt")
data class Inntekt(val forslag: ForslagInntekt) : ForespurtDataDto()

@Serializable
@SerialName("FastsattInntekt")
data class FastsattInntekt(val fastsattInntekt: Double) : ForespurtDataDto()

@Serializable
@SerialName("Refusjon")
object Refusjon : ForespurtDataDto()

@Serializable
data class ForslagInntekt(
    val beregningsmåneder: List<YearMonth>
)
