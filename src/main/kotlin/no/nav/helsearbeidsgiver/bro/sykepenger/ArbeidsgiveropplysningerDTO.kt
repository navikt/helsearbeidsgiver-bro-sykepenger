package no.nav.helsearbeidsgiver.bro.sykepenger

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDate
import java.time.LocalDateTime

internal data class ArbeidsgiveropplysningerDTO(
    val type: Meldingstype,
    val organisasjonsnummer: String,
    val fødselsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val opprettet: LocalDateTime = LocalDateTime.now()
) {
    val meldingstype get() = type.name.lowercase().toByteArray()
    constructor(message: JsonMessage) : this(
        type = Meldingstype.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER,
        organisasjonsnummer = message["organisasjonsnummer"].asText(),
        fødselsnummer = message["fødselsnummer"].asText(),
        fom = message["fom"].asLocalDate(),
        tom = message["tom"].asLocalDate(),
        opprettet = message["@opprettet"].asLocalDateTime()
    )
}
internal enum class Meldingstype {
    TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER
}
