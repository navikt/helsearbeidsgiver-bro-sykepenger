@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class ForespoerselDto(
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val forespurtData: List<ForespurtDataDto>,
    val forespoerselBesvart: LocalDateTime?,
    val status: Status,
    val opprettet: LocalDateTime = LocalDateTime.now().truncMillis(),
    val oppdatert: LocalDateTime = LocalDateTime.now().truncMillis()
)

@Serializable
sealed class ForespurtDataDto

@Serializable
data class ArbeidsgiverPeriode(val forslag: List<Forslag>) : ForespurtDataDto()

@Serializable
object Refusjon : ForespurtDataDto()

@Serializable
object Inntekt : ForespurtDataDto()

@Serializable
data class Forslag(
    val fom: LocalDate,
    val tom: LocalDate
)

enum class Status {
    TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER,
    BESVART,
    FORKASTET,
    AVBRUTT
}
