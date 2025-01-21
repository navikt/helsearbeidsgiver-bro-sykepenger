package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

@Serializable
data class HentForespoerslerForFnrOgOrgnrSvar(
    val orgnr: Orgnr,
    val fnr: Fnr,
    val resultat: List<ForespoerselSimba>,
    val boomerang: JsonElement,
) {
    companion object {
        val behovType = Pri.BehovType.HENT_FORESPOERSLER_FOR_FNR_OG_ORGNR
    }
}
