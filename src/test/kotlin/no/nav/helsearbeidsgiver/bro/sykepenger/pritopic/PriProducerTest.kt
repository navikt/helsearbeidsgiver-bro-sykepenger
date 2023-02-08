package no.nav.helsearbeidsgiver.bro.sykepenger.pritopic

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.errors.TimeoutException
import java.util.UUID

class PriProducerTest : FunSpec({
    val mockProducer = mockk<KafkaProducer<String, String>>()

    val priProducer = PriProducer(
        producer = mockProducer
    )

    beforeEach {
        clearAllMocks()
    }

    test("gir true ved sendt melding til kafka stream") {
        every { mockProducer.send(any()).get() } returns mockRecordMetadata()

        val forespoerselMottatt = mockForespoerselMottatt()

        val bleMeldingSendt = priProducer.send(forespoerselMottatt, ForespoerselMottatt::toJson)

        bleMeldingSendt.shouldBeTrue()

        val expected = ProducerRecord<String, String>(
            "helsearbeidsgiver.pri",
            forespoerselMottatt.toJson().toString()
        )

        verifySequence { mockProducer.send(expected) }
    }

    test("gir false ved feilet sending til kafka stream") {
        every { mockProducer.send(any()) } throws TimeoutException("too slow bro")

        val forespoerselMottatt = mockForespoerselMottatt()

        val bleMeldingSendt = priProducer.send(forespoerselMottatt, ForespoerselMottatt::toJson)

        bleMeldingSendt.shouldBeFalse()

        verifySequence { mockProducer.send(any()) }
    }
})

private fun mockForespoerselMottatt(): ForespoerselMottatt =
    ForespoerselMottatt(
        forespoerselId = UUID.randomUUID(),
        orgnr = "123",
        fnr = "abc"
    )

private fun mockRecordMetadata(): RecordMetadata =
    RecordMetadata(null, 0, 0, 0, 0, 0)
