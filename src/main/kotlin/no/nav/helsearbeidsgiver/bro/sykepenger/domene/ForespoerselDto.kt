package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
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

enum class Status {
    AKTIV,
    BESVART,
    FORKASTET
}
