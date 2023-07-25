@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class, YearMonthSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.YearMonthSerializer
import java.time.LocalDate
import java.time.YearMonth

@Serializable
data class ForespurtData(
    val arbeidsgiverperiode: Arbeidsgiverperiode,
    val inntekt: Inntekt,
    val refusjon: Refusjon
)

@Serializable
data class Arbeidsgiverperiode(
    val paakrevd: Boolean
)

@Serializable
data class Inntekt(
    val paakrevd: Boolean,
    val forslag: ForslagInntekt
)

@Serializable
data class Refusjon(
    val paakrevd: Boolean,
    val forslag: ForslagRefusjon
) {
    companion object {
        fun ikkePaakrevd(): Refusjon =
            Refusjon(
                paakrevd = false,
                forslag = ForslagRefusjon(
                    perioder = emptyList(),
                    opphoersdato = null
                )
            )
    }
}

@Serializable
sealed class ForslagInntekt {
    @Serializable
    @SerialName("ForslagInntektGrunnlag")
    // TODO erstatt med skjæringstidspunkt?
    data class Grunnlag(val beregningsmaaneder: List<YearMonth>) : ForslagInntekt()

    @Serializable
    @SerialName("ForslagInntektFastsatt")
    data class Fastsatt(val fastsattInntekt: Double) : ForslagInntekt()
}

@Serializable
data class ForslagRefusjon(
    val perioder: List<Periode>,
    val opphoersdato: LocalDate?
) {
    @Serializable
    data class Periode(
        val fom: LocalDate,
        val beloep: Double
    )
}
