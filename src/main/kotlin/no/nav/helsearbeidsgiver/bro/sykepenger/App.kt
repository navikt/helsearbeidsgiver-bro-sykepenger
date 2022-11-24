package no.nav.helsearbeidsgiver.bro.sykepenger

import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.bro.sykepenger.db.DataSourceBuilder
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import org.slf4j.LoggerFactory

fun main() {
    val broLogger = LoggerFactory.getLogger("BroLogger")
    broLogger.info("Hello bro!")

    val env = System.getenv()
    val dataSourceBuilder = DataSourceBuilder(env)
    val dataSource by lazy { dataSourceBuilder.getDataSource() }

    val connection = RapidApplication.create(env)

    ForespoerselRiver(
        rapidsConnection = connection,
        forespoerselDao = ForespoerselDao(dataSource)
    )

    connection.registerDatasource(dataSourceBuilder, dataSource)

    connection.start()
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
