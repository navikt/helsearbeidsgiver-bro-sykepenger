package no.nav.helsearbeidsgiver.bro.sykepenger

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.bro.sykepenger.db.DataSourceBuilder
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import org.slf4j.LoggerFactory

fun main() {
    val broLogger = LoggerFactory.getLogger("BroLogger")
    broLogger.info("Hello bro!")

    val env = System.getenv()
    val dataSource = DataSourceBuilder(env).getDataSource()
    val forespoerselDao = ForespoerselDao(dataSource)

    RapidApplication.create(env).apply {
        ForespoerselRiver(this, forespoerselDao)
    }.start()
}
