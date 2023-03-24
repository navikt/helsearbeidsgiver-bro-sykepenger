package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import kotlinx.serialization.Serializable

private val whitespaceRgx = Regex("\\s")
private val orgnrRgx = Regex("\\d{9}")

@Serializable
@JvmInline
value class Orgnr(val verdi: String) {
    init {
        require(verdi.erGyldigOrgnr())
    }

    override fun toString(): String =
        verdi
}

fun String.parseKommaSeparertOrgnrListe(): List<Orgnr> {
    if (isEmpty()) return emptyList()

    val (
        gyldigeOrgnr,
        ugyldigeOrgnr
    ) = replace(whitespaceRgx, "")
        .split(",")
        .partition(String::erGyldigOrgnr)

    if (ugyldigeOrgnr.isNotEmpty()) {
        throw RuntimeException("Tillatte organisasjoner i pilot inneholder ugyldig orgnr: $ugyldigeOrgnr")
    }

    return gyldigeOrgnr.map(::Orgnr)
}

private fun String.erGyldigOrgnr(): Boolean =
    matches(orgnrRgx)
