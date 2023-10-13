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

    object AzureAD {
        val scope = "SPINN_SCOPE".fromEnv()
        val azureWellKnownUrl = "AZURE_APP_WELL_KNOWN_URL".fromEnv()
        val azureTokenEndpointUrl = "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT".fromEnv()
        val azureAppClientID = "AZURE_APP_CLIENT_ID".fromEnv()
        val azureAppClientSecret = "AZURE_APP_CLIENT_SECRET".fromEnv()
        val azureAppJwk = "AZURE_APP_JWK".fromEnv()
    }

    fun String.fromEnv(): String =
        System.getenv(this)
            ?: throw RuntimeException("Missing required environment variable \"$this\".")
}
