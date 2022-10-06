package no.nav.helsearbeidsgiver.bro.sykepenger

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

class BroApp {
    private var webserver: NettyApplicationEngine? = null
    private val port = 8080

    fun configAndStartWebserver() {
        webserver = embeddedServer(
            Netty,
            applicationEngineEnvironment {
                connector {
                    port = this@BroApp.port
                }

                module {
                    routing {
                        get("/is-alive") {
                            call.respondText("ALIVE", ContentType.Text.Plain)
                        }

                        get("/is-ready") {
                            call.respondText("READY", ContentType.Text.Plain)
                        }
                    }
                }
            }
        )

        // OBS skru av wait = true før prodsetting
        webserver!!.start(wait = true)
    }

    fun shutdown() {
        webserver!!.stop(1000, 1000)
    }
}

fun main() {
    val broLogger = LoggerFactory.getLogger("BroLogger")
    broLogger.info("Hello bro!")
    val app = BroApp()
    app.configAndStartWebserver()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            broLogger.info("Fikk shutdown-signal, avslutter...")
            app.shutdown()
            broLogger.info("Avsluttet OK")
        }
    )
}
