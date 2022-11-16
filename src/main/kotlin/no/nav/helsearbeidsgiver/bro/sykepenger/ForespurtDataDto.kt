@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonClassDiscriminator
import java.time.LocalDate

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("opplysningstype")
sealed class ForespurtDataDto

@Serializable
@SerialName("Arbeidsgiverperiode")
data class ArbeidsgiverPeriode(val forslag: List<Forslag>) : ForespurtDataDto()

@Serializable
@SerialName("Refusjon")
object Refusjon : ForespurtDataDto()

@Serializable
@SerialName("Inntekt")
object Inntekt : ForespurtDataDto()

@Serializable
data class Forslag(
    val fom: LocalDate,
    val tom: LocalDate
)
