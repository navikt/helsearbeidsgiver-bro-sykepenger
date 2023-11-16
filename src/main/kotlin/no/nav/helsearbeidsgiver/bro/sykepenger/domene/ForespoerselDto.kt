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
    val type: Type,
    val status: Status,
    val orgnr: Orgnr,
    val fnr: String,
    val vedtaksperiodeId: UUID,
    val skjaeringstidspunkt: LocalDate?,
    val sykmeldingsperioder: List<Periode>,
    val egenmeldingsperioder: List<Periode>,
    val forespurtData: List<SpleisForespurtDataDto>,
    val besvarelse: BesvarelseMetadataDto?,
    val opprettet: LocalDateTime = LocalDateTime.now().truncMillis(),
    val oppdatert: LocalDateTime = LocalDateTime.now().truncMillis(),
) {
    fun erDuplikatAv(other: ForespoerselDto): Boolean =
        this ==
            other.copy(
                forespoerselId = forespoerselId,
                besvarelse = besvarelse,
                opprettet = opprettet,
                oppdatert = oppdatert,
            )
}

data class BesvarelseMetadataDto(
    val forespoerselBesvart: LocalDateTime,
    val inntektsmeldingId: UUID?,
)

enum class Status {
    AKTIV,
    BESVART_SIMBA,
    BESVART_SPLEIS,
    FORKASTET,
    ;

    fun erBesvart(): Boolean = this in listOf(BESVART_SIMBA, BESVART_SPLEIS)
}

enum class Type {
    /** En komplett forespørsel tilhører en vanlig vedtaksperiode og kjenner til hvilke opplysninger vedtaksperioden trenger fra arbeidsgiver. */
    KOMPLETT,

    /**
     * En begrenset forespørsel tilhører en vedtaksperiode som ble sendt til Infotrygd før den fikk tilstrekkelig informasjon om opplysningene som trengs.
     *
     * En begrenset forespørsel:
     *   - ber alltid om Inntekt, Refusjon og Arbeidsgiverperiode og sender aldri med forslag til hva disse skal være (forespurt data)
     *   - mangler skjæringstidspunkt
     */
    BEGRENSET,

    /**
     * En potensiell forespørsel er knyttet til en vedtaksperiode som er innenfor arbeidsgiverperioden.
     *
     * Slike perioder trenger ingen arbeidsgiveropplysninger, men skal tillate å motta opplysninger fra arbeidsgiver
     * fordi de kan ha opplysninger som gjør at perioden strekker seg forbi arbeidsgiverperioden.
     */
    POTENSIELL,
}

@Serializable
data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
)
