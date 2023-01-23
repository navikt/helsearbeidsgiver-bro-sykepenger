package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.jsonOf
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson
import java.util.UUID

data class ForespoerselSvar(
    val forespoerselId: UUID,
    val orgnr: String,
    val fnr: String,
    val sykmeldingsperioder: List<Periode>,
    val forespurtData: List<ForespurtDataDto>,
    val boomerang: Map<String, JsonElement>
) {
    val løsning = Pri.BehovType.TRENGER_FORESPØRSEL

    constructor(forespoersel: ForespoerselDto, boomerang: Map<String, JsonElement>) : this(
        forespoerselId = forespoersel.forespoerselId,
        orgnr = forespoersel.orgnr,
        fnr = forespoersel.fnr,
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        forespurtData = forespoersel.forespurtData,
        boomerang = boomerang
    )

    fun toJson(): JsonElement =
        jsonOf(
            Pri.Key.LØSNING to løsning.toJson(),
            Pri.Key.ORGNR to orgnr.toJson(),
            Pri.Key.FNR to fnr.toJson(),
            Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Pri.Key.SYKMELDINGSPERIODER to sykmeldingsperioder.toJson(Json::encodeToJsonElement),
            Pri.Key.FORESPURT_DATA to forespurtData.toJson(Json::encodeToJsonElement),
            Pri.Key.BOOMERANG to boomerang.toJson()
        )
}
