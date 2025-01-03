@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

@Serializable
data class ForespoerselMottatt(
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val fnr: Fnr,
    @SerialName("skal_ha_paaminnelse")
    val skalHaPaaminnelse: Boolean,
    val forespoersel: ForespoerselSimba,
)
