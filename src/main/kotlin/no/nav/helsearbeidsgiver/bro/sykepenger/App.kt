package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.bro.sykepenger.db.Database
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer
import no.nav.helsearbeidsgiver.utils.log.logger

fun main() {
    val broLogger = "BroLogger".logger()
    broLogger.info("Hello bro!")

    val forespoerselDao = ForespoerselDao(Database.db)
    val priProducer = PriProducer()

    val rapid = RapidApplication.create(System.getenv())

    LagreKomplettForespoerselRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)
    TilgjengeliggjoerForespoerselRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)
    TilgjengeliggjoerForespoerslerForFnrOgOrgnrRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)
    TilgjengeliggjoerForespoerslerForVedtaksperiodeIdListeRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)
    MarkerBesvartFraSimbaRiver(rapid = rapid, forespoerselDao = forespoerselDao)
    MarkerBesvartFraSpleisRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)
    ForkastForespoerselRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)
    LagreBegrensetForespoerselRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)
    MarkerKastetTilInfotrygdRiver(rapid = rapid, forespoerselDao = forespoerselDao, priProducer = priProducer)

    rapid.registerDbLifecycle()

    rapid.start()
}

private fun RapidsConnection.registerDbLifecycle() {
    register(
        object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                Database.migrate()
            }

            override fun onShutdown(rapidsConnection: RapidsConnection) {
                Database.dataSource.close()
            }
        },
    )
}
