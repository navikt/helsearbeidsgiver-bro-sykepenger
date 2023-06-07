@file:UseSerializers(LocalDateSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class ForespoerselDto(
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val skjaeringstidspunkt: LocalDate,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val forespurtData: List<ForespurtDataDto>,
    val forespoerselBesvart: LocalDateTime?,
    val status: Status,
    val type: Type,
    val opprettet: LocalDateTime = LocalDateTime.now().truncMillis(),
    val oppdatert: LocalDateTime = LocalDateTime.now().truncMillis()
)

enum class Status {
    AKTIV,
    BESVART,
    FORKASTET
}

enum class Type {
    KOMPLETT,
    BEGRENSET,
    POTENSIELL
}

@Serializable
data class Periode(
    val fom: LocalDate,
    val tom: LocalDate
)
