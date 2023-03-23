@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.bro.sykepenger.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.UuidSerializer
import java.util.UUID

@Serializable
data class ForespoerselMottatt(
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val fnr: String
) {
    companion object {
        val notisType = Pri.NotisType.FORESPØRSEL_MOTTATT
    }
}
