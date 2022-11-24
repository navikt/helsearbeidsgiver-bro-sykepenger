package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.Serializable

@Serializable
data class ForespoerselMottatt(
    val orgnr: String,
    val fnr: String
) {
    val eventType = "FORESPØRSEL_MOTTATT"
}
