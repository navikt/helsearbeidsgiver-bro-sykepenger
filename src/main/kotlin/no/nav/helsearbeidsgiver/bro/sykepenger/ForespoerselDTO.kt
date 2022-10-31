package no.nav.helsearbeidsgiver.bro.sykepenger

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal data class ForespoerselDTO(
    val organisasjonsnummer: String,
    val fødselsnummer: String,
    val vedtaksperiodeId: UUID,
    val vedtaksperiodeFom: LocalDate,
    val vedtaksperiodeTom: LocalDate,
    val behov: BehovDTO,
    val status: Status?,
    val forespoerselBesvart: LocalDateTime?,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val oppdatert: LocalDateTime = LocalDateTime.now()
)
internal enum class Status {
    TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER,
    BESVART,
    FORKASTET,
    AVBRUTT
}

internal class BehovDTO
