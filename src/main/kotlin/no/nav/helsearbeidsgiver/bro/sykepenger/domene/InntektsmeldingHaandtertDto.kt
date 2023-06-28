package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import java.time.LocalDateTime
import java.util.UUID

data class InntektsmeldingHaandtertDto(
    val orgnr: Orgnr,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val inntektsmeldingId: UUID?,
    val haandtert: LocalDateTime
)
