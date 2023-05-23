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
    val skjaeringstidspunkt: LocalDate?,
    val sykmeldingsperioder: List<Periode>,
    val forespurtData: List<ForespurtDataDto>?,
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

    /* En komplett forespørsel tilhører en vanlig vedtaksperiode og kjenner til hvilke opplysninger vedtaksperioden trenger fra arbeidsgiver */
    KOMPLETT,

    /* En begrenset forespørsel tilhører en vedtaksperiode som ble sendt til Infotrygd før den fikk tilstrekkelig informasjon om opplysningene som trengs
    *
    * En begrenset forespørsel mangler:
    *   - hvilke opplysninger vi trenger fra arbeidsgiver (forespurt data)
    *   - skjæringstidspunkt
    *   - andre sykmeldingsperioder enn den forkastede perioden som er knyttet til arbeidsgiverperioden
    * */
    BEGRENSET,

    /* En potensiell forespørsel er knyttet til en vedtaksperiode som er innenfor arbeidsgiverperioden

    * Slike perioder trenger ingen arbeidsgiveropplysninger, men skal tillate å motta opplysninger fra arbeidsgiver
    * fordi de kan ha opplysninger som gjør at perioden strekker seg forbi arbeidsgiverperioden
    * */
    POTENSIELL
}

@Serializable
data class Periode(
    val fom: LocalDate,
    val tom: LocalDate
)
