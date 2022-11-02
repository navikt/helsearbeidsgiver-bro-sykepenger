package no.nav.helsearbeidsgiver.bro.sykepenger

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal data class ForespoerselDto(
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val fom: LocalDate,
    val tom: LocalDate,
    val forespurtData: ForespurtDataDto,
    val forespoerselBesvart: LocalDateTime?,
    val status: Status,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = LocalDateTime.now()
)

internal enum class Status {
    TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER,
    BESVART,
    FORKASTET,
    AVBRUTT
}

internal class ForespurtDataDto {
    fun toJson() = "{}"
}
