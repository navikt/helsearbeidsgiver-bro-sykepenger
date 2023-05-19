package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class Loggernaut<T : Any>(
    loggingClass: T
) {
    val aapen = loggingClass.logger()
    val sikker = sikkerLogger()

    private val seSikkerLogg = "Se sikker logg for mer info."

    fun ukjentFeil(feil: Throwable) {
        "Ukjent feil.".let {
            aapen.error("$it $seSikkerLogg")
            sikker.error(it, feil)
        }
    }

    fun innkommendeMeldingFeil(problems: MessageProblems) {
        "Innkommende melding har feil.".let {
            aapen.error("$it $seSikkerLogg")
            sikker.error("$it Detaljer:\n${problems.toExtendedReport()}")
        }
    }
}
