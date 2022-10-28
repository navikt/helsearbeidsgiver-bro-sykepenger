package no.nav.helsearbeidsgiver.bro.sykepenger

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.bro.sykepenger.db.DataSourceBuilder
import org.slf4j.LoggerFactory

fun main() {
    val broLogger = LoggerFactory.getLogger("BroLogger")
    broLogger.info("Hello bro!")
    val env = System.getenv()
    val dataSourceBuilder = DataSourceBuilder(env)
    val dataSource = dataSourceBuilder.getDataSource()
    RapidApplication.create(env).apply {
        ForespoerselRiver(this)
    }.start()
}
