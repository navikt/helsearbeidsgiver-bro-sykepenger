@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class ForespurtData(
    val arbeidsgiverperiode: Arbeidsgiverperiode,
    val inntekt: Inntekt,
    val refusjon: Refusjon,
)

@Serializable
data class Arbeidsgiverperiode(
    val paakrevd: Boolean,
)

@Serializable
data class Inntekt(
    val paakrevd: Boolean,
    val forslag: ForslagInntekt?,
) {
    companion object {
        fun ikkePaakrevd(): Inntekt =
            Inntekt(
                paakrevd = false,
                forslag = null,
            )
    }
}

@Serializable
data class Refusjon(
    val paakrevd: Boolean,
    val forslag: ForslagRefusjon,
) {
    companion object {
        fun ikkePaakrevd(): Refusjon =
            Refusjon(
                paakrevd = false,
                forslag =
                    ForslagRefusjon(
                        perioder = emptyList(),
                        opphoersdato = null,
                    ),
            )
    }
}

@Serializable
data class ForslagInntekt(
    val forrigeInntekt: ForrigeInntekt?,
)

@Serializable
data class ForslagRefusjon(
    val perioder: List<Periode>,
    val opphoersdato: LocalDate?,
) {
    @Serializable
    data class Periode(
        val fom: LocalDate,
        val beloep: Double,
    )
}

@Serializable
data class ForrigeInntekt(
    val skjæringstidspunkt: LocalDate,
    val kilde: String,
    val beløp: Double,
)
