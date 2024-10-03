package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.Key
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.spleis.Spleis
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.toJsonStr

fun TestRapid.sendJson(vararg keyValuePairs: Pair<Key, JsonElement?>) {
    keyValuePairs.toMap()
        .mapValuesNotNull { it }
        .toJsonStr()
        .let(this::sendTestMessage)
}

private fun Map<Key, JsonElement>.toJsonStr(): String =
    tryToJsonStr(Pri.Key.serializer())
        ?: tryToJsonStr(Spleis.Key.serializer())
        ?: throw RuntimeException("Klarte ikke å serialisere testmelding.")

@Suppress("UNCHECKED_CAST")
private fun <K : Key> Map<Key, JsonElement>.tryToJsonStr(serializer: KSerializer<K>): String? =
    (this as? Map<K, JsonElement>)?.toJsonStr(
        MapSerializer(
            serializer,
            JsonElement.serializer(),
        ),
    )
