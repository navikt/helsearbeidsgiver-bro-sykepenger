@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.tilForespurtData
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.time.LocalDate
import java.util.UUID

@Serializable
data class ForespoerselSvar(
    val forespoerselId: UUID,
    val resultat: Suksess? = null,
    val feil: Feil? = null,
    val boomerang: JsonElement,
) {
    companion object {
        val behovType = Pri.BehovType.TRENGER_FORESPØRSEL
    }

    @Serializable
    data class Suksess(
        val type: Type,
        val orgnr: Orgnr,
        val fnr: String,
        val vedtaksperiodeId: UUID,
        val egenmeldingsperioder: List<Periode>,
        val sykmeldingsperioder: List<Periode>,
        val skjaeringstidspunkt: LocalDate?,
        val bestemmendeFravaersdager: Map<Orgnr, LocalDate>,
        val forespurtData: ForespurtData,
        val erBesvart: Boolean,
    ) {
        constructor(forespoersel: ForespoerselDto) : this(
            type = forespoersel.type,
            orgnr = forespoersel.orgnr,
            fnr = forespoersel.fnr,
            vedtaksperiodeId = forespoersel.vedtaksperiodeId,
            egenmeldingsperioder = forespoersel.egenmeldingsperioder,
            sykmeldingsperioder = forespoersel.sykmeldingsperioder,
            skjaeringstidspunkt = forespoersel.skjaeringstidspunkt,
            bestemmendeFravaersdager = forespoersel.bestemmendeFravaersdager,
            forespurtData = forespoersel.forespurtData.tilForespurtData(),
            erBesvart = forespoersel.status.erBesvart(),
        )
    }

    enum class Feil {
        FORESPOERSEL_IKKE_FUNNET,
    }
}
