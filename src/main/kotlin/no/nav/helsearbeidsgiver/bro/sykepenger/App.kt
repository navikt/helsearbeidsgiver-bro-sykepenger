package no.nav.helsearbeidsgiver.bro.sykepenger

import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.LoggerFactory

fun main() {
    val broLogger = LoggerFactory.getLogger("BroLogger")
    broLogger.info("Hello bro!")
    RapidApplication.create(System.getenv()).apply {
        ForespoerselRiver(this)
    }.start()
}
