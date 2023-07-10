package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic

interface Key {
    val verdi: String
}

fun <K : Key, V : Any> Map<K, V>.keysAsString(): Map<String, V> =
    mapKeys { (key, _) -> key.verdi }
