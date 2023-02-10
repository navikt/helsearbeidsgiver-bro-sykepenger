@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.UuidSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import java.util.UUID

@Serializable
data class ForespoerselSvar(
    val forespoerselId: UUID,
    val resultat: Suksess? = null,
    val feil: Feil? = null,
    val boomerang: Map<String, JsonElement>
) {
    companion object {
        val behovType = Pri.BehovType.TRENGER_FORESPØRSEL
    }

    @Serializable
    data class Suksess(
        val orgnr: String,
        val fnr: String,
        val sykmeldingsperioder: List<Periode>,
        val forespurtData: List<ForespurtDataDto>
    ) {
        constructor(forespoersel: ForespoerselDto) : this(
            orgnr = forespoersel.orgnr,
            fnr = forespoersel.fnr,
            sykmeldingsperioder = forespoersel.sykmeldingsperioder,
            forespurtData = forespoersel.forespurtData
        )
    }

    enum class Feil {
        FORESPOERSEL_IKKE_FUNNET
    }
}
