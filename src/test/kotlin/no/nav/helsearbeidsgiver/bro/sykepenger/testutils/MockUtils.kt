package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ArbeidsgiverPeriode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.FastsattInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.parseJson
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson
import java.util.UUID

object MockUuid {
    val vedtaksperiodeId: UUID = "01234567-abcd-0123-abcd-012345678901".let(UUID::fromString)
    val forespoerselId: UUID = "98765654-abcd-0123-abcd-012345678901".let(UUID::fromString)
}

fun mockForespoerselDto(): ForespoerselDto =
    ForespoerselDto(
        forespoerselId = randomUuid(),
        orgnr = "123456789".let(::Orgnr),
        fnr = "123456789",
        vedtaksperiodeId = MockUuid.vedtaksperiodeId,
        skjaeringstidspunkt = 15.januar,
        sykmeldingsperioder = listOf(
            Periode(1.januar, 10.januar),
            Periode(15.januar, 20.januar)
        ),
        forespurtData = mockForespurtDataListe(),
        forespoerselBesvart = null,
        status = Status.AKTIV
    )

fun mockForespurtDataListe(): List<ForespurtDataDto> =
    listOf(
        ArbeidsgiverPeriode(
            forslag = listOf(
                Periode(
                    fom = 1.januar,
                    tom = 10.januar
                ),
                Periode(
                    fom = 15.januar,
                    tom = 20.januar
                )
            )
        ),
        Inntekt(
            forslag = ForslagInntekt(
                beregningsmåneder = listOf(
                    oktober(2017),
                    november(2017),
                    desember(2017)
                )
            )
        ),
        Refusjon(forslag = emptyList())
    )

fun mockForespurtDataMedFastsattInntektListe(): List<ForespurtDataDto> =
    listOf(
        ArbeidsgiverPeriode(
            forslag = listOf(
                Periode(
                    fom = 1.januar,
                    tom = 10.januar
                ),
                Periode(
                    fom = 15.januar,
                    tom = 20.januar
                )
            )
        ),
        FastsattInntekt(
            fastsattInntekt = 31415.92
        ),
        Refusjon(
            listOf(
                ForslagRefusjon(1.januar, null, 31415.92)
            )
        )
    )

fun mockForespoerselMottatt(): ForespoerselMottatt =
    ForespoerselMottatt(
        forespoerselId = randomUuid(),
        orgnr = "287429436".let(::Orgnr),
        fnr = "abc"
    )

fun mockForespoerselSvarSuksess(): ForespoerselSvar.Suksess =
    ForespoerselSvar.Suksess(
        orgnr = "569046822".let(::Orgnr),
        fnr = "abc",
        sykmeldingsperioder = listOf(Periode(1.januar, 16.januar)),
        forespurtData = mockForespurtDataListe()
    )

fun mockJsonElement(): JsonElement =
    """{"aTestKey":"aTestValue"}""".parseJson()

fun ForespoerselMottatt.toKeyMap() =
    mapOf(
        Pri.Key.NOTIS to ForespoerselMottatt.notisType.toJson(Pri.NotisType.serializer()),
        Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson(),
        Pri.Key.ORGNR to orgnr.toJson(Orgnr.serializer()),
        Pri.Key.FNR to fnr.toJson()
    )
