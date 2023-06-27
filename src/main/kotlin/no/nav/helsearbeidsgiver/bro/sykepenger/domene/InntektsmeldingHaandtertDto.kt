package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import java.time.LocalDateTime
import java.util.UUID

data class InntektsmeldingHaandtertDto(
    val orgnr: Orgnr,
    val vedtaksperiodeId: UUID,
    val fnr: String,
    val dokumentId: UUID?,
    val opprettet: LocalDateTime
)
