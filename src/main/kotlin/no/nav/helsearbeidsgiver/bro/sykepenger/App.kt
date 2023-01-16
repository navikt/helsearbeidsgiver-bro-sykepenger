package no.nav.helsearbeidsgiver.bro.sykepenger

import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.bro.sykepenger.db.DataSourceBuilder
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.pritopic.PriProducer
import org.slf4j.LoggerFactory

fun main() {
    val broLogger = LoggerFactory.getLogger("BroLogger")
    broLogger.info("Hello bro!")

    val dataSourceBuilder = DataSourceBuilder()
    val dataSource by lazy { dataSourceBuilder.getDataSource() }
    val forespoerselDao = ForespoerselDao(dataSource)
    val priProducer = PriProducer()

    val rapid = RapidApplication.create(System.getenv())

    LagreForespoerselRiver(
        rapid = rapid,
        forespoerselDao = forespoerselDao,
        priProducer = priProducer
    )

    TilgjengeliggjoerForespoerselRiver(
        rapid = rapid,
        forespoerselDao = forespoerselDao,
        priProducer = priProducer
    )

    rapid.registerDatasource(dataSourceBuilder, dataSource)

    rapid.start()
}

private fun RapidsConnection.registerDatasource(dataSourceBuilder: DataSourceBuilder, dataSource: HikariDataSource) {
    register(object : RapidsConnection.StatusListener {
        override fun onStartup(rapidsConnection: RapidsConnection) {
            dataSourceBuilder.migrate()
        }
        override fun onShutdown(rapidsConnection: RapidsConnection) {
            dataSource.close()
        }
    })
}
