package no.nav.helsearbeidsgiver.bro.sykepenger.utils

/**
 * Prioriterer `last` over `leading`.
 * For en liste med størrelse 1 vil det eneste elementet ende i `last`, og `leading` blir tom.
 *
 * @return `null` dersom listen er tom.
 */
fun <T : Any> List<T>.leadingAndLast(): Pair<List<T>, T>? =
    lastOrNull()?.let {
        Pair(
            dropLast(1),
            it,
        )
    }

fun <T : Any> List<T>.zipWithNextOrNull(): List<Pair<T, T?>> =
    windowed(size = 2, partialWindows = true)
        .map {
            val current = it[0]
            val next = it.getOrNull(1)

            Pair(current, next)
        }
