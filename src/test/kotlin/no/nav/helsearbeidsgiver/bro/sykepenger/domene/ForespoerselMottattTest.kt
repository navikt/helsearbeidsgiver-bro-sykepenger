package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselMottatt
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.test.json.removeJsonWhitespace

class ForespoerselMottattTest :
    FunSpec({
        test("data serialiseres korrekt") {
            val forespoerselMottatt = mockForespoerselMottatt()

            val expectedJson = forespoerselMottatt.hardcodedJson()

            val actualJson = forespoerselMottatt.toJsonStr(ForespoerselMottatt.serializer())

            actualJson shouldBe expectedJson
        }

        test("data deserialiseres korrekt") {
            val expectedInstance = mockForespoerselMottatt()

            val expectedJson = expectedInstance.hardcodedJson()

            val actualInstance =
                shouldNotThrowAny {
                    expectedJson.parseJson().fromJson(ForespoerselMottatt.serializer())
                }

            actualInstance shouldBe expectedInstance
        }
    })

private fun ForespoerselMottatt.hardcodedJson(): String =
    """
    {
        "${Pri.Key.FORESPOERSEL_ID}": "$forespoerselId",
        "${Pri.Key.ORGNR}": "$orgnr",
        "${Pri.Key.FNR}": "$fnr",
        "${Pri.Key.SKAL_HA_PAAMINNELSE}": $skalHaPaaminnelse,
        "${Pri.Key.FORESPOERSEL}": ${forespoersel.toJsonStr(ForespoerselSimba.serializer())}
    }
    """.removeJsonWhitespace()
