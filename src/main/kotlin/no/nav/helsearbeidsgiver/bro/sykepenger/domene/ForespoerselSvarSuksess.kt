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
    val resultat: ForespoerselSvarSuksess? = null,
    val feil: ForespoerselSvarFeil? = null,
    val boomerang: Map<String, JsonElement>
) {
    fun toJson(): JsonElement = listOf(
        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        Pri.Key.RESULTAT to resultat?.toJson(),
        Pri.Key.FEIL to feil?.toJson(),
        Pri.Key.BOOMERANG to boomerang.toJson()
    )
        .mapNotNull { (key, value) -> if (value != null) key to value else null }
        .toTypedArray()
        .let(::jsonOf)
}

enum class ForespoerselSvarFeil(val feilmelding: String) {
    FORESPOERSEL_IKKE_FUNNET("Fant ikke forespørsel i databasen for gitte forespørselId.");

    fun toJson(): JsonElement = jsonOf(
        Pri.Key.FEILKODE to name.toJson(),
        Pri.Key.FEILMELDING to feilmelding.toJson(),
    )
}

data class ForespoerselSvarSuksess(
    val orgnr: String,
    val fnr: String,
    val sykmeldingsperioder: List<Periode>,
    val forespurtData: List<ForespurtDataDto>,
) {
    val løsning = Pri.BehovType.TRENGER_FORESPØRSEL

    constructor(forespoersel: ForespoerselDto) : this(
        orgnr = forespoersel.orgnr,
        fnr = forespoersel.fnr,
        sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        forespurtData = forespoersel.forespurtData,
    )

    fun toJson(): JsonElement =
        jsonOf(
            Pri.Key.LØSNING to løsning.toJson(),
            Pri.Key.ORGNR to orgnr.toJson(),
            Pri.Key.FNR to fnr.toJson(),
            Pri.Key.SYKMELDINGSPERIODER to sykmeldingsperioder.toJson(Json::encodeToJsonElement),
            Pri.Key.FORESPURT_DATA to forespurtData.toJson(Json::encodeToJsonElement),
        )
}
