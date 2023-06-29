@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.tilForespurtData
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
data class ForespoerselSvar(
    val forespoerselId: UUID,
    val resultat: Suksess? = null,
    val feil: Feil? = null,
    val boomerang: JsonElement
) {
    companion object {
        val behovType = Pri.BehovType.TRENGER_FORESPØRSEL
    }

    @Serializable
    data class Suksess(
        val orgnr: Orgnr,
        val fnr: String,
        val sykmeldingsperioder: List<Periode>,
        val egenmeldingsperioder: List<Periode>,
        val forespurtData: ForespurtData
    ) {
        constructor(forespoersel: ForespoerselDto) : this(
            orgnr = forespoersel.orgnr,
            fnr = forespoersel.fnr,
            sykmeldingsperioder = forespoersel.sykmeldingsperioder,
            egenmeldingsperioder = forespoersel.egenmeldingsperioder,
            forespurtData = forespoersel.forespurtData.tilForespurtData()
        )
    }

    enum class Feil {
        FORESPOERSEL_IKKE_FUNNET
    }
}
