package no.nav.helsearbeidsgiver.bro.sykepenger

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
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
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = LocalDateTime.now()
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "opplysningstype"
)
@JsonSubTypes(
    JsonSubTypes.Type(ArbeidsgiverPeriode::class),
    JsonSubTypes.Type(Refusjon::class),
    JsonSubTypes.Type(Inntekt::class)
)
sealed class ForespurtDataDto

data class ArbeidsgiverPeriode(val forslag: List<Forslag>) : ForespurtDataDto()

object Refusjon : ForespurtDataDto()

object Inntekt : ForespurtDataDto()

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
