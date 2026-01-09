package no.nav.helsearbeidsgiver.bro.sykepenger

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helsearbeidsgiver.bro.sykepenger.db.Database
import no.nav.helsearbeidsgiver.bro.sykepenger.db.ForespoerselDao
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.PriProducer

fun main() {
    val forespoerselDao = ForespoerselDao(Database.db)

    val priProducer = PriProducer()

    val rapid = RapidApplication.create(System.getenv())

    LagreKomplettForespoerselRiver(rapid, forespoerselDao, priProducer)
    LagreBegrensetForespoerselRiver(rapid, forespoerselDao, priProducer)

    TilgjengeliggjoerForespoerselRiver(rapid, forespoerselDao, priProducer)
    TilgjengeliggjoerForespoerslerForFnrOgOrgnrRiver(rapid, forespoerselDao, priProducer)
    TilgjengeliggjoerForespoerslerForVedtaksperiodeIdListeRiver(rapid, forespoerselDao, priProducer)

    MarkerBesvartFraSimbaRiver(rapid, forespoerselDao)
    MarkerBesvartFraSpleisRiver(rapid, forespoerselDao, priProducer)
    ForkastForespoerselRiver(rapid, forespoerselDao, priProducer)
    MarkerKastetTilInfotrygdRiver(rapid, forespoerselDao, priProducer)

    // Midlertidig river for at LPS-appen kan hente oppdaterte forespørsler
    HentForespoerselRiver(rapid, forespoerselDao, priProducer)

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
