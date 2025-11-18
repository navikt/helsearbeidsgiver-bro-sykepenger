package no.nav.helsearbeidsgiver.bro.sykepenger.utils

/**
 * Prioriterer `last` over `leading`.
 * For en liste med størrelse 1 vil det eneste elementet ende i `last`, og `leading` blir tom.
 *
 * @return `null` dersom listen er tom.
 */
fun <T : Any> List<T>.leadingAndLast(): Pair<List<T>, T>? =
    when (size) {
        0 -> {
            null
        }

        1 -> {
            Pair(emptyList(), first())
        }

        else -> {
            val (leading, onlyLast) = chunked(size - 1)

            onlyLast
                .firstOrNull()
                ?.let { last ->
                    Pair(leading, last)
                }
        }
    }

fun <T : Any> List<T>.zipWithNextOrNull(): List<Pair<T, T?>> =
    windowed(size = 2, partialWindows = true)
        .map {
            val current = it[0]
            val next = it.getOrNull(1)

            Pair(current, next)
        }
