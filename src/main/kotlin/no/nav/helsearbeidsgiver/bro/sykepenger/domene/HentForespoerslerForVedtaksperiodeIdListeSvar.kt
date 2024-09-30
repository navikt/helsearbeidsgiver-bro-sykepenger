package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri

@Serializable
data class HentForespoerslerForVedtaksperiodeIdListeSvar(
    val resultat: List<ForespoerselSimba>,
    val boomerang: JsonElement,
) {
    companion object {
        val behovType = Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE
    }
}
