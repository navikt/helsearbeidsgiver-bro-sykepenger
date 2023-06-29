package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtData
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisArbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisFastsattInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForslagInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.bro.sykepenger.kafkatopic.pri.Pri
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
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
            Periode(2.januar, 10.januar),
            Periode(15.januar, 20.januar)
        ),
        egenmeldingsperioder = listOf(Periode(1.januar, 1.januar)),
        forespurtData = mockSpleisForespurtDataListe(),
        forespoerselBesvart = null,
        type = Type.KOMPLETT,
        status = Status.AKTIV
    )

fun mockSpleisForespurtDataListe(): List<SpleisForespurtDataDto> =
    listOf(
        SpleisArbeidsgiverperiode,
        SpleisInntekt(
            forslag = SpleisForslagInntekt(
                beregningsmåneder = listOf(
                    august(1999),
                    september(1999),
                    oktober(1999)
                )
            )
        ),
        SpleisRefusjon(
            forslag = listOf(
                SpleisForslagRefusjon(
                    fom = 12.juni,
                    tom = null,
                    beløp = 21.31
                ),
                SpleisForslagRefusjon(
                    fom = 2.august,
                    tom = 15.august,
                    beløp = 44.77
                )
            )
        )
    )

fun mockBegrensetForespurtDataListe(): List<SpleisForespurtDataDto> =
    listOf(
        SpleisArbeidsgiverperiode,
        SpleisInntekt(forslag = SpleisForslagInntekt(beregningsmåneder = emptyList())),
        SpleisRefusjon(forslag = emptyList())
    )

fun mockForespurtDataMedFastsattInntektListe(): List<SpleisForespurtDataDto> =
    listOf(
        SpleisArbeidsgiverperiode,
        mockSpleisFastsattInntekt(),
        SpleisRefusjon(
            forslag = listOf(
                SpleisForslagRefusjon(
                    fom = 1.januar,
                    tom = null,
                    beløp = 31415.92
                )
            )
        )
    )

fun mockSpleisFastsattInntekt(): SpleisFastsattInntekt =
    SpleisFastsattInntekt(
        fastsattInntekt = 31415.92
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
        sykmeldingsperioder = listOf(Periode(2.januar, 16.januar)),
        egenmeldingsperioder = listOf(Periode(1.januar, 1.januar)),
        forespurtData = mockForespurtData()
    )

fun mockForespurtData(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode = mockArbeidsgiverperiode(),
        inntekt = mockInntektMedForslagGrunnlag(),
        refusjon = mockRefusjon()
    )

fun mockArbeidsgiverperiode(): Arbeidsgiverperiode =
    Arbeidsgiverperiode(
        paakrevd = true
    )

fun mockInntektMedForslagGrunnlag(): Inntekt =
    Inntekt(
        paakrevd = true,
        forslag = ForslagInntekt.Grunnlag(
            beregningsmaaneder = listOf(
                august(1999),
                september(1999),
                oktober(1999)
            )
        )
    )

fun mockInntektMedForslagFastsatt(): Inntekt =
    Inntekt(
        paakrevd = false,
        forslag = ForslagInntekt.Fastsatt(
            fastsattInntekt = 31415.92
        )
    )

fun mockRefusjon(): Refusjon =
    Refusjon(
        paakrevd = true,
        forslag = ForslagRefusjon(
            perioder = listOf(
                ForslagRefusjon.Periode(
                    fom = 12.juni,
                    beloep = 21.31
                ),
                ForslagRefusjon.Periode(
                    fom = 2.august,
                    beloep = 44.77
                )
            ),
            opphoersdato = 15.august
        )
    )

fun mockInntektsmeldingHaandtertDto(): InntektsmeldingHaandtertDto =
    InntektsmeldingHaandtertDto(
        vedtaksperiodeId = MockUuid.vedtaksperiodeId,
        orgnr = "287429436".let(::Orgnr)
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
