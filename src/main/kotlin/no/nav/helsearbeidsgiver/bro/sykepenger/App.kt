package no.nav.helsearbeidsgiver.bro.sykepenger

import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.bro.sykepenger.db.DataSourceBuilder
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.utils.log.logger

fun main() {
    val broLogger = "BroLogger".logger()
    broLogger.info("Hello bro!")

    if (Env.AllowList.organisasjoner.isEmpty()) {
        broLogger.error("Listen med tillatte organisasjoner i pilot er tom!")
    }

    val dataSourceBuilder = DataSourceBuilder()
    val dataSource by lazy { dataSourceBuilder.getDataSource() }
    val forespoerselDao = ForespoerselDao(dataSource)
    val priProducer = PriProducer()

    val rapid = RapidApplication.create(System.getenv())

    LagreForespoerselRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)
    LagreKomplettForespoerselRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)
    TilgjengeliggjoerForespoerselRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)

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
