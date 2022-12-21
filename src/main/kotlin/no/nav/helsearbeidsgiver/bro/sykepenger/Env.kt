package no.nav.helsearbeidsgiver.bro.sykepenger

object Env {
    object Kafka {
        val brokers = "KAFKA_BROKERS".fromEnv()
        val keystorePath = "KAFKA_KEYSTORE_PATH".fromEnv()
        val truststorePath = "KAFKA_TRUSTSTORE_PATH".fromEnv()
        val credstorePassword = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
    }
}

private fun String.fromEnv(): String =
    System.getenv(this)
        ?: throw RuntimeException("Missing required environment variable \"$this\".")
