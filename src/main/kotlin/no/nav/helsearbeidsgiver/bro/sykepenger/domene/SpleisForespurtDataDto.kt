package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("opplysningstype")
sealed class SpleisForespurtDataDto

@Serializable
@SerialName("Arbeidsgiverperiode")
data object SpleisArbeidsgiverperiode : SpleisForespurtDataDto()

@Serializable
@SerialName("Inntekt")
data object SpleisInntekt : SpleisForespurtDataDto()

@Deprecated("Ikke lenger i bruk, men trengs for å deserialisere gamle rader i databasen (siste opprettet 2025-01-27)")
@Serializable
@SerialName("FastsattInntekt")
data object SpleisFastsattInntekt : SpleisForespurtDataDto()

@Serializable
@SerialName("Refusjon")
data object SpleisRefusjon : SpleisForespurtDataDto()
