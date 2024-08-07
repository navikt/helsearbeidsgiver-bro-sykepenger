@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer

@Serializable
data class HentForespoerslerForFnrOgOrgnrSvar(
    val orgnr: Orgnr,
    val fnr: String,
    val resultat: List<Suksess>,
    val boomerang: JsonElement,
) {
    companion object {
        val behovType = Pri.BehovType.HENT_FORESPOERSLER_FOR_FNR_OG_ORGNR
    }
}
