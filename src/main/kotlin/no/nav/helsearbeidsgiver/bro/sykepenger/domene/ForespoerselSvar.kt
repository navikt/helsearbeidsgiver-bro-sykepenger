@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import java.util.UUID

@Serializable
data class ForespoerselSvar(
    val forespoerselId: UUID,
    val resultat: ForespoerselSimba? = null,
    val feil: Feil? = null,
    val boomerang: JsonElement,
) {
    companion object {
        val behovType = Pri.BehovType.TRENGER_FORESPØRSEL
    }

    enum class Feil {
        FORESPOERSEL_IKKE_FUNNET,
    }
}
