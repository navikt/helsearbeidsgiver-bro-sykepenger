package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable

private val orgnrRgx = Regex("\\d{9}")

@Serializable
@JvmInline
value class Orgnr(
    val verdi: String,
) {
    init {
        require(verdi.erGyldigOrgnr())
    }

    override fun toString(): String = verdi
}

private fun String.erGyldigOrgnr(): Boolean = matches(orgnrRgx)
