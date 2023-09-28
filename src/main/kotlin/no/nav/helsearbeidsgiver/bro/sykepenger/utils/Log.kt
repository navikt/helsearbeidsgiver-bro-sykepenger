package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import java.util.UUID

object Log {
    fun <T : Any> klasse(value: T): Pair<String, String> =
        "class" to value.simpleName()

    fun forespoerselId(value: UUID): Pair<String, String> =
        "forespoersel_id" to value.toString()

    fun type(value: Type): Pair<String, String> =
        "forespoersel_type" to value.name

    fun vedtaksperiodeId(value: UUID): Pair<String, String> =
        "vedtaksperiode_id" to value.toString()
}

private fun <T : Any> T.simpleName(): String =
    this::class.simpleName.orEmpty()
