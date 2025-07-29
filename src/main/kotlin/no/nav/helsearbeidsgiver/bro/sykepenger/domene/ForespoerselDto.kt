@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class, LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.truncMillis
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class ForespoerselDto(
    val forespoerselId: UUID,
    val type: Type,
    val status: Status,
    val orgnr: Orgnr,
    val fnr: Fnr,
    val vedtaksperiodeId: UUID,
    val egenmeldingsperioder: List<Periode>,
    val sykmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdager: Map<Orgnr, LocalDate>,
    val forespurtData: List<SpleisForespurtDataDto>,
    val opprettet: LocalDateTime = LocalDateTime.now().truncMillis(),
    val oppdatert: LocalDateTime = LocalDateTime.now().truncMillis(),
    val kastetTilInfotrygd: LocalDateTime? = null,
) {
    fun erDuplikatAv(other: ForespoerselDto): Boolean =
        this ==
            other.copy(
                forespoerselId = forespoerselId,
                opprettet = opprettet,
                oppdatert = oppdatert,
            )
}

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
     *   - mangler bestemmende fraværsdager
     */
    BEGRENSET,
}

@Serializable
data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
)

infix fun LocalDate.til(tom: LocalDate): Periode =
    Periode(
        fom = this,
        tom = tom,
    )

@Serializable
data class ForespoerselTilLpsApi(
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val fnr: Fnr,
    val vedtaksperiodeId: UUID,
    val egenmeldingsperioder: List<Periode>,
    val sykmeldingsperioder: List<Periode>,
    val status: Status,
    val bestemmendeFravaersdager: Map<Orgnr, LocalDate>,
    val forespurtData: List<SpleisForespurtDataDto>,
    val eksponertForespoerselId: UUID,
    val opprettet: LocalDateTime,
)
