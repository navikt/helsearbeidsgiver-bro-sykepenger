package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.errors.TimeoutException

class PriProducerTest : FunSpec({
    val mockProducer = mockk<KafkaProducer<String, ForespoerselMottatt>>()

    val priProducer = PriProducer(
        producer = mockProducer
    )

    beforeTest {
        clearAllMocks()
    }

    test("gir true ved sendt melding til kafka stream") {
        every { mockProducer.send(any()).get() } returns mockRecordMetadata()

        val forespoerselMottatt = mockForespoerselMottatt()

        val bleMeldingSendt = priProducer.send(forespoerselMottatt)

        bleMeldingSendt.shouldBeTrue()

        verifySequence { mockProducer.send(any()) }
    }

    test("gir false ved feilet sending til kafka stream") {
        every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

        val forespoerselMottatt = mockForespoerselMottatt()

        val bleMeldingSendt = priProducer.send(forespoerselMottatt)

        bleMeldingSendt.shouldBeFalse()

        verifySequence { mockProducer.send(any()) }
    }
})

private fun mockForespoerselMottatt(): ForespoerselMottatt =
    ForespoerselMottatt(
        orgnr = "123",
        fnr = "abc"
    )

private fun mockRecordMetadata(): RecordMetadata =
    RecordMetadata(null, 0, 0, 0, 0, 0)
