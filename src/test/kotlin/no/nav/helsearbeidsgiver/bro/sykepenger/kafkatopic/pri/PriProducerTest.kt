package no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.tilMeldingForespoerselMottatt
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.errors.TimeoutException

class PriProducerTest :
    FunSpec({
        val mockProducer = mockk<KafkaProducer<String, String>>()

        val priProducer =
            PriProducer(
                producer = mockProducer,
            )

        beforeEach {
            clearAllMocks()
        }

        test("gir true ved sendt melding til kafka stream") {
            every { mockProducer.send(any()).get() } returns mockRecordMetadata()

            val forespoersel = mockForespoerselDto()

            val bleMeldingSendt =
                priProducer.send(
                    *forespoersel.tilMeldingForespoerselMottatt(),
                )

            bleMeldingSendt.shouldBeTrue()

            val expected =
                ProducerRecord<String, String>(
                    Pri.TOPIC,
                    forespoersel.tilMeldingForespoerselMottatt().toMap().toJsonStr(),
                )

            verifySequence { mockProducer.send(expected) }
        }

        test("gir false ved feilet sending til kafka stream") {
            every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

            val forespoersel = mockForespoerselDto()

            val bleMeldingSendt =
                priProducer.send(
                    *forespoersel.tilMeldingForespoerselMottatt(),
                )

            bleMeldingSendt.shouldBeFalse()

            verifySequence { mockProducer.send(any()) }
        }
    })

private fun mockRecordMetadata(): RecordMetadata = RecordMetadata(null, 0, 0, 0, 0, 0)
