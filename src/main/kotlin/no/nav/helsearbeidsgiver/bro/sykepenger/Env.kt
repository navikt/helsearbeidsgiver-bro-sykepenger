package no.nav.helsearbeidsgiver.bro.sykepenger

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.parseKommaSeparertOrgnrListe

object Env {
    object VarName {
        const val PILOT_TILLATTE_ORGANISASJONER = "PILOT_TILLATTE_ORGANISASJONER"
    }

    object Kafka {
        val brokers = "KAFKA_BROKERS".fromEnv()
        val keystorePath = "KAFKA_KEYSTORE_PATH".fromEnv()
        val truststorePath = "KAFKA_TRUSTSTORE_PATH".fromEnv()
        val credstorePassword = "KAFKA_CREDSTORE_PASSWORD".fromEnv()
    }

    object Database {
        private const val prefix: String = "NAIS_DATABASE_HELSEARBEIDSGIVER_BRO_SYKEPENGER_HELSEARBEIDSGIVER_BRO_SYKEPENGER"
        val name = "${prefix}_DATABASE".fromEnv()
        val host = "${prefix}_HOST".fromEnv()
        val port = "${prefix}_PORT".fromEnv()
        val username = "${prefix}_USERNAME".fromEnv()
        val password = "${prefix}_PASSWORD".fromEnv()
    }

    object AllowList {
        // Bruker en get() her for å få testene til å fungere uten for mye knot
        // En helt OK løsning for kode som ikke skal leve så lenge
        val organisasjoner get() =
            VarName.PILOT_TILLATTE_ORGANISASJONER.fromEnv()
                .parseKommaSeparertOrgnrListe()
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
