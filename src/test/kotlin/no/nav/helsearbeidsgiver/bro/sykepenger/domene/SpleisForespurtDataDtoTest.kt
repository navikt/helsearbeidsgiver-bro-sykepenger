package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockSpleisForespurtDataListe
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace
import no.nav.helsearbeidsgiver.utils.test.resource.readResource

class SpleisForespurtDataDtoTest :
    FunSpec({
        val expectedJson = "json/forespurtDataListe.json".readResource().removeJsonWhitespace()

        test("Forespurt data serialiseres korrekt") {
            val forespurtDataListe = mockSpleisForespurtDataListe()

            val serialisertJson = forespurtDataListe.toJsonStr(SpleisForespurtDataDto.serializer().list())

            serialisertJson shouldBe expectedJson
        }

        test("Forespurt data deserialiseres korrekt") {
            val forespurtDataListe = mockSpleisForespurtDataListe()

            val deserialisertJson = expectedJson.parseJson().fromJson(SpleisForespurtDataDto.serializer().list())

            deserialisertJson shouldContainExactly forespurtDataListe
        }
    })
