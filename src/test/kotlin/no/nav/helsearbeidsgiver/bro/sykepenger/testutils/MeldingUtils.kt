package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

fun ForespoerselDto.tilMeldingForespoerselMottatt(skalHaPaaminnelse: Boolean = true): Array<Pair<Pri.Key, JsonElement>> =
    arrayOf(
        Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        Pri.Key.FORESPOERSEL to ForespoerselSimba(this).toJson(ForespoerselSimba.serializer()),
        Pri.Key.SKAL_HA_PAAMINNELSE to skalHaPaaminnelse.toJson(Boolean.serializer()),
    )

fun ForespoerselDto.tilMeldingForespoerselOppdatert(eksponertForespoerselId: UUID): Array<Pair<Pri.Key, JsonElement>> =
    arrayOf(
        Pri.Key.NOTIS to Pri.NotisType.FORESPOERSEL_OPPDATERT.toJson(Pri.NotisType.serializer()),
        Pri.Key.EKSPONERT_FORESPOERSEL_ID to eksponertForespoerselId.toJson(),
        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        Pri.Key.FORESPOERSEL to ForespoerselSimba(this).toJson(ForespoerselSimba.serializer()),
    )
