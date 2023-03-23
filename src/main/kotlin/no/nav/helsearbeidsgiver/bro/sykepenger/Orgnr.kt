package no.nav.helsearbeidsgiver.bro.sykepenger

import kotlinx.serialization.Serializable

private val orgnrRgx = Regex("\\d{9}")

@Serializable
@JvmInline
value class Orgnr(val verdi: String) {
    init {
        require(verdi.erGyldigOrgnr())
    }
}

private fun String.erGyldigOrgnr(): Boolean =
    matches(orgnrRgx)
