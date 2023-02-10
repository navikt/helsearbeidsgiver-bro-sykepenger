@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.bro.sykepenger.UuidSerializer
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import java.util.UUID

@Serializable
data class ForespoerselMottatt(
    val forespoerselId: UUID,
    val orgnr: String,
    val fnr: String
) {
    companion object {
        val notisType = Pri.NotisType.FORESPØRSEL_MOTTATT
    }
}
