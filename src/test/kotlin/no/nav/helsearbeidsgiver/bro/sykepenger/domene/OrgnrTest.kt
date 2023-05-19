package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr

class OrgnrTest : FunSpec({
    test("data serialiseres korrekt") {
        val orgnr = mockOrgnr()

        val expectedJson = orgnr.hardcodedJson()

        val actualJson = orgnr.toJsonStr(Orgnr.serializer())

        actualJson shouldBe expectedJson
    }

    test("data deserialiseres korrekt") {
        val expectedInstance = mockOrgnr()

        val expectedJson = expectedInstance.hardcodedJson()

        val actualInstance = shouldNotThrowAny {
            expectedJson.parseJson().fromJson(Orgnr.serializer())
        }

        actualInstance shouldBe expectedInstance
    }

    test("Tom streng er gyldig og parses korrekt") {
        val organisasjoner = "".parseKommaSeparertOrgnrListe()

        organisasjoner.shouldBeEmpty()
    }

    test("Streng med ett gyldig orgnr parses korrekt") {
        val organisasjoner = "429834723".parseKommaSeparertOrgnrListe()

        organisasjoner.shouldContainExactly(
            "429834723".let(::Orgnr)
        )
    }

    test("Streng med gyldige orgnr parses korrekt") {
        val organisasjoner = "429834723,494234909,761702603".parseKommaSeparertOrgnrListe()

        organisasjoner.shouldContainExactly(
            "429834723".let(::Orgnr),
            "494234909".let(::Orgnr),
            "761702603".let(::Orgnr)
        )
    }

    test("Streng med gyldige orgnr og whitespace parses korrekt") {
        val organisasjoner = "  42983 4723,4942\n34909, 761702603\n ".parseKommaSeparertOrgnrListe()

        organisasjoner.shouldContainExactly(
            "429834723".let(::Orgnr),
            "494234909".let(::Orgnr),
            "761702603".let(::Orgnr)
        )
    }

    context("Streng med ugyldige orgnr gir exception med detaljer") {
        withData(
            nameFn = { (input, _) -> input },
            row("429834723,4942349a9,ikkeEtTallEngang", listOf("4942349a9", "ikkeEtTallEngang")),
            row("4321,llama", listOf("4321", "llama")),
            row("1_2", listOf("1_2")),
            row("429834723.494234909,761702603", listOf("429834723.494234909")),
            row(",", listOf("", ""))
        ) { (organisasjonerStr, ugyldigeOrgnr) ->
            val parseException = shouldThrowExactly<RuntimeException> {
                organisasjonerStr.parseKommaSeparertOrgnrListe()
            }

            parseException.message shouldBe "Tillatte organisasjoner i pilot inneholder ugyldig orgnr: $ugyldigeOrgnr"
        }
    }
})

private fun mockOrgnr(): Orgnr =
    Orgnr("885927409")

private fun Orgnr.hardcodedJson(): String =
    "\"$verdi\""
