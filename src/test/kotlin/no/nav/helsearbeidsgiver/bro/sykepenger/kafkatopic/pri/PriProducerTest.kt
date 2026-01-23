package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri

import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.tilMeldingForespoerselMottatt
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.UUID
import java.util.concurrent.TimeoutException

class PriProducerTest :
    FunSpec(
        {
            val mockProducer = mockk<KafkaProducer<String, String>>()

            val priProducer =
                PriProducer(
                    producer = mockProducer,
                )

            beforeEach {
                clearAllMocks()
            }

            listOf(
                UUID.randomUUID(),
                Fnr.genererGyldig(),
            ).forEach { kafkaKey ->
                context("${kafkaKey::class.simpleName} som Kafka-nøkkel") {
                    test("sending av melding er vellykket") {
                        every { mockProducer.send(any()).get() } returns mockRecordMetadata()

                        val melding = mockForespoerselDto().tilMeldingForespoerselMottatt()

                        shouldNotThrowAny {
                            priProducer.send(kafkaKey, melding)
                        }

                        val expected =
                            ProducerRecord(
                                Pri.TOPIC,
                                kafkaKey.toString(),
                                melding.toMap().toJsonStr(),
                            )

                        verifySequence { mockProducer.send(expected) }
                    }

                    test("sending av melding feiler") {
                        every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

                        val melding = mockForespoerselDto().tilMeldingForespoerselMottatt()

                        shouldThrow<TimeoutException> {
                            priProducer.send(kafkaKey, melding)
                        }

                        verifySequence { mockProducer.send(any()) }
                    }
                }
            }
        },
    )

private fun PriProducer.send(
    kafkaKey: Any,
    melding: Array<Pair<Pri.Key, JsonElement>>,
) {
    when (kafkaKey) {
        is UUID -> send(kafkaKey, *melding)
        is Fnr -> send(kafkaKey, *melding)
        else -> fail("Kafka-nøkkel av type ${kafkaKey::class.simpleName} er ikke støttet.")
    }
}

private fun mockRecordMetadata(): RecordMetadata = RecordMetadata(null, 0, 0, 0, 0, 0)
