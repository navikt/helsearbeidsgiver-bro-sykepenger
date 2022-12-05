package no.nav.helsearbeidsgiver.bro.sykepenger

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeEqualComparingTo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.januar
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mockForespurtDataListe
import java.util.UUID

class ForespoerselSvarTest : FunSpec({
    test("data serialiseres korrekt") {
        val forespoerselSvar = ForespoerselSvar(
            orgnr = "123",
            fnr = "abc",
            vedtaksperiodeId = UUID.randomUUID(),
            fom = 1.januar,
            tom = 16.januar,
            forespurtData = mockForespurtDataListe()
        )

        val expectedJson = """
            {
                "orgnr": "${forespoerselSvar.orgnr}",
                "fnr": "${forespoerselSvar.fnr}",
                "vedtaksperiodeId": "${forespoerselSvar.vedtaksperiodeId}",
                "fom": "${forespoerselSvar.fom}",
                "tom": "${forespoerselSvar.tom}",
                "forespurtData": ${forespoerselSvar.forespurtData.let(Json::encodeToString)},
                "eventType": "${forespoerselSvar.eventType}"
            }
        """.removeJsonWhitespace()

        val actualJson = forespoerselSvar.toJson().toString()

        actualJson shouldBeEqualComparingTo expectedJson
    }
})
