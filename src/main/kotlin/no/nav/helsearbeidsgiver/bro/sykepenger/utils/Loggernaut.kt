package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class Loggernaut<T : Any>(
    loggingClass: T,
) {
    val aapen = loggingClass.logger()
    val sikker = sikkerLogger()

    private val seSikkerLogg = "Se sikker logg for mer info."

    fun info(melding: String) {
        aapen.info(melding)
        sikker.info(melding)
    }

    fun warn(melding: String) {
        aapen.warn(melding)
        sikker.warn(melding)
    }

    fun error(melding: String) {
        aapen.error(melding)
        sikker.error(melding)
    }

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
