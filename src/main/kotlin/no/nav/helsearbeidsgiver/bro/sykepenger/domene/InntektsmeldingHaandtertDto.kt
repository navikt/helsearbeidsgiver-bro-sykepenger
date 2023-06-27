package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import java.util.UUID

data class InntektsmeldingHaandtertDto(
    val orgnr: Orgnr,
    val vedtaksperiodeId: UUID,
    val fnr: String,
    val dokumentId: UUID?
)
