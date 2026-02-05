package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.tilMeldingForespoerselMottatt
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

            test("sending av melding er vellykket") {
                val kafkaKey = UUID.randomUUID()

                every { mockProducer.send(any()).get() } returns mockRecordMetadata()

                val melding = mockForespoerselDto().tilMeldingForespoerselMottatt()

                shouldNotThrowAny {
                    priProducer.send(kafkaKey, *melding)
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
                val kafkaKey = UUID.randomUUID()

                every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

                val melding = mockForespoerselDto().tilMeldingForespoerselMottatt()

                shouldThrow<TimeoutException> {
                    priProducer.send(kafkaKey, *melding)
                }

                verifySequence { mockProducer.send(any()) }
            }
        },
    )

private fun mockRecordMetadata(): RecordMetadata = RecordMetadata(null, 0, 0, 0, 0, 0)
