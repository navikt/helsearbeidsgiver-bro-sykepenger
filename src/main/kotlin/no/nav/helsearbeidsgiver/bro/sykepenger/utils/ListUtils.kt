package no.nav.helsearbeidsgiver.bro.sykepenger.utils

/**
 * Prioriterer `last` over `leading`.
 * For en liste med størrelse 1 vil det eneste elementet ende i `last`, og `leading` blir tom.
 *
 * @return `null` dersom listen er tom.
 */
fun <T : Any> List<T>.leadingAndLast(): Pair<List<T>, T>? {
    val (leading, onlyLast) = partitionIndexed { index, _ ->
        index != size - 1
    }

    return onlyLast.firstOrNull()
        ?.let { last ->
            Pair(leading, last)
        }
}

fun <T : Any, R : Any> List<T>.mapWithNext(transform: (T, T?) -> R): List<R> =
    windowed(size = 2, partialWindows = true)
        .map {
            val current = it[0]
            val next = it.getOrNull(1)

            transform(current, next)
        }

fun <T : Any> List<T>.partitionIndexed(predicate: (Int, T) -> Boolean): Pair<List<T>, List<T>> =
    withIndex()
        .partition {
            predicate(it.index, it.value)
        }
        .let { (yieldedTrue, yieldedFalse) ->
            Pair(
                yieldedTrue.map { it.value },
                yieldedFalse.map { it.value }
            )
        }
