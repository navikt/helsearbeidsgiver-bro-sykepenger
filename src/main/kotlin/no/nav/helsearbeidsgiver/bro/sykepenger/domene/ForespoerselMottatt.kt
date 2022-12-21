package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.jsonOf
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson
import java.util.UUID

data class ForespoerselMottatt(
    val orgnr: String,
    val fnr: String,
    val vedtaksperiodeId: UUID
) {
    val notis = Pri.NotisType.FORESPØRSEL_MOTTATT

    fun toJson(): JsonElement =
        jsonOf(
            Pri.Key.NOTIS to notis.toJson(),
            Pri.Key.ORGNR to orgnr.toJson(),
            Pri.Key.FNR to fnr.toJson(),
            Pri.Key.VEDTAKSPERIODE_ID to vedtaksperiodeId.toJson()
        )
}
