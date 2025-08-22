@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.bro.sykepenger.tilForespurtData
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID

@Serializable
data class ForespoerselSimba(
    val orgnr: Orgnr,
    val fnr: Fnr,
    val forespoerselId: UUID,
    val vedtaksperiodeId: UUID,
    val egenmeldingsperioder: List<Periode>,
    val sykmeldingsperioder: List<Periode>,
    val bestemmendeFravaersdager: Map<Orgnr, LocalDate>,
    val forespurtData: ForespurtData,
    val erBesvart: Boolean,
    val erBegrenset: Boolean,
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
        erBesvart = forespoersel.status.erBesvart(),
        erBegrenset = forespoersel.type == Type.BEGRENSET,
    )

    constructor(forespoersel: ForespoerselDtoMedEksponertFsp) : this(
        orgnr = forespoersel.orgnr,
        fnr = forespoersel.fnr,
        forespoerselId = forespoersel.forespoerselId,
        vedtaksperiodeId = forespoersel.vedtaksperiodeId,
        egenmeldingsperioder = forespoersel.egenmeldingsperioder,
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager,
        forespurtData = forespoersel.forespurtData.tilForespurtData(),
        erBesvart = forespoersel.status.erBesvart(),
        erBegrenset = forespoersel.type == Type.BEGRENSET,
    )
}
