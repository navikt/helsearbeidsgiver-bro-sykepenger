package no.nav.helsearbeidsgiver.bro.sykepenger

object Env {
    object Kafka {
        val brokers = "KAFKA_BROKERS".fromEnv()
        val keystorePath = "KAFKA_KEYSTORE_PATH".fromEnv()
        val truststorePath = "KAFKA_TRUSTSTORE_PATH".fromEnv()
        val credstorePassword = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
    }

    object Database {
        private const val PREFIX: String = "NAIS_DATABASE_HELSEARBEIDSGIVER_BRO_SYKEPENGER_HELSEARBEIDSGIVER_BRO_SYKEPENGER"
        val name = "${PREFIX}_DATABASE".fromEnv()
        val host = "${PREFIX}_HOST".fromEnv()
        val port = "${PREFIX}_PORT".fromEnv()
        val username = "${PREFIX}_USERNAME".fromEnv()
        val password = "${PREFIX}_PASSWORD".fromEnv()
    }

    fun String.fromEnv(): String =
        System.getenv(this)
            ?: throw RuntimeException("Missing required environment variable \"$this\".")
}
