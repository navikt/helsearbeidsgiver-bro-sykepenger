@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.bro.sykepenger.tilForespurtData
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
data class ForespoerselSimba(
    val type: Type,
    val orgnr: Orgnr,
    val fnr: String,
    val forespoerselId: UUID,
    val vedtaksperiodeId: UUID,
    val egenmeldingsperioder: List<Periode>,
    val sykmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdager: Map<Orgnr, LocalDate>,
    val forespurtData: ForespurtData,
    val erBesvart: Boolean,
    val opprettet: LocalDate,
) {
    constructor(forespoersel: ForespoerselDto) : this(
        type = forespoersel.type,
        orgnr = forespoersel.orgnr,
        fnr = forespoersel.fnr,
        forespoerselId = forespoersel.forespoerselId,
        vedtaksperiodeId = forespoersel.vedtaksperiodeId,
        egenmeldingsperioder = forespoersel.egenmeldingsperioder,
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager,
        forespurtData = forespoersel.forespurtData.tilForespurtData(),
        erBesvart = forespoersel.status.erBesvart(),
        opprettet = forespoersel.opprettet.toLocalDate(),
    )
}
