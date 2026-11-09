@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class, LocalDateTimeSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.bro.sykepenger.tilForespurtData
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Serializable
class ForespoerselHagAdmin(
    val orgnr: Orgnr,
    val fnr: Fnr,
    val forespoerselId: UUID,
    val vedtaksperiodeId: UUID,
    val egenmeldingsperioder: List<Periode>,
    val sykmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdager: Map<Orgnr, LocalDate>,
    val forespurtData: ForespurtData,
    val status: String,
    val erBegrenset: Boolean,
    val opprettet: LocalDateTime,
) {
    constructor(forespoersel: ForespoerselDto) : this(
        orgnr = forespoersel.orgnr,
        fnr = forespoersel.fnr,
        forespoerselId = forespoersel.forespoerselId,
        vedtaksperiodeId = forespoersel.vedtaksperiodeId,
        egenmeldingsperioder = forespoersel.egenmeldingsperioder,
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager,
        forespurtData = forespoersel.forespurtData.tilForespurtData(),
        status = forespoersel.status.tilHagAdminStatus(),
        erBegrenset = forespoersel.type == Type.BEGRENSET,
        opprettet = forespoersel.opprettet,
    )
}

private fun Status.tilHagAdminStatus(): String =
    when (this) {
        Status.AKTIV -> "Aktiv"
        Status.BESVART_SIMBA, Status.BESVART_SPLEIS -> "Besvart"
        Status.FORKASTET -> "Forkastet"
    }
