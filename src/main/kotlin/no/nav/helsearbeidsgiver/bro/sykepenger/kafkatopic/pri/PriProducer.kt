package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.Env
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import java.util.UUID

class PriProducer(
    private val producer: KafkaProducer<String, String> = createProducer(),
) {
    private val loggernaut = Loggernaut(this)

    private val topic = Pri.TOPIC

    fun send(
        kafkaKey: UUID,
        vararg keyValuePairs: Pair<Pri.Key, JsonElement>,
    ) {
        send(kafkaKey.toString(), keyValuePairs)
    }

    fun send(
        kafkaKey: Fnr,
        vararg keyValuePairs: Pair<Pri.Key, JsonElement>,
    ) {
        send(kafkaKey.verdi, keyValuePairs)
    }

    private fun send(
        kafkaKey: String,
        keyValuePairs: Array<out Pair<Pri.Key, JsonElement>>,
    ) {
        val kafkaMessage = keyValuePairs.toMap().toJsonStr()
        val record = ProducerRecord(topic, kafkaKey, kafkaMessage)

        runCatching {
            producer.send(record).get()
        }.onFailure { error ->
            "Klarte ikke sende melding til topic '$topic'.".also {
                loggernaut.aapen.error(it)
                loggernaut.sikker.error("$it\nKey: '$kafkaKey'\nMelding: '$kafkaMessage'", error)
            }
            throw error
        }
    }
}

fun Map<Pri.Key, JsonElement>.toJsonStr() =
    toJson(
        MapSerializer(
            Pri.Key.serializer(),
            JsonElement.serializer(),
        ),
    ).toString()

private fun createProducer(): KafkaProducer<String, String> =
    KafkaProducer(
        kafkaProperties(),
        StringSerializer(),
        StringSerializer(),
    )

private fun kafkaProperties(): Properties =
    Properties().apply {
        putAll(
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to Env.Kafka.brokers,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "jks",
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to Env.Kafka.truststorePath,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to Env.Kafka.credstorePassword,
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to Env.Kafka.keystorePath,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to Env.Kafka.credstorePassword,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
            ),
        )
    }
