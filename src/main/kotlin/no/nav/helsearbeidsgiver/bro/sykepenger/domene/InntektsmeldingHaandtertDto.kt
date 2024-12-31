package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDateTime
import java.util.UUID

data class InntektsmeldingHaandtertDto(
    val orgnr: Orgnr,
    val fnr: Fnr,
    val vedtaksperiodeId: UUID,
    val inntektsmeldingId: UUID?,
    val haandtert: LocalDateTime,
)
