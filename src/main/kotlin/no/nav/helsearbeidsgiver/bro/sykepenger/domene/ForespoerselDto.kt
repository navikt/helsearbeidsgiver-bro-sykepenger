@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.bro.sykepenger.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.LocalDateSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class ForespoerselDto(
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val sykmeldingsperioder: List<Periode>,
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

@Serializable
data class Periode(
    val fom: LocalDate,
    val tom: LocalDate
)
