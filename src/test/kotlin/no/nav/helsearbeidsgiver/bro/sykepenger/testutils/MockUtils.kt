package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.BesvarelseMetadataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Orgnr
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
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
import java.time.LocalDateTime
import java.util.UUID

object MockUuid {
    val vedtaksperiodeId: UUID = "01234567-abcd-0123-abcd-012345678901".let(UUID::fromString)
    val forespoerselId: UUID = "98765654-abcd-0123-abcd-012345678901".let(UUID::fromString)
    val inntektsmeldingId: UUID = "22efb342-3e72-4880-a449-eb1efcf0f18b".let(UUID::fromString)
}

fun mockForespoerselDto(besvarelseMetaData: BesvarelseMetadataDto? = null): ForespoerselDto =
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
        forespurtData = mockForespurtDataListe(),
        status = Status.AKTIV,
        type = Type.KOMPLETT,
        besvarelse = besvarelseMetaData
    )

fun mockForespurtDataListe(): List<SpleisForespurtDataDto> =
    listOf(
        SpleisArbeidsgiverperiode,
        SpleisInntekt(
            forslag = SpleisForslagInntekt(
                beregningsmåneder = listOf(
                    oktober(2017),
                    november(2017),
                    desember(2017)
                )
            )
        ),
        SpleisRefusjon(
            forslag = listOf(
                SpleisForslagRefusjon(17.mai, null, 13.37)
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
        SpleisFastsattInntekt(
            fastsattInntekt = 31415.92
        ),
        SpleisRefusjon(
            listOf(
                SpleisForslagRefusjon(1.januar, null, 31415.92)
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
        sykmeldingsperioder = listOf(Periode(2.januar, 16.januar)),
        egenmeldingsperioder = listOf(Periode(1.januar, 1.januar)),
        forespurtData = mockForespurtDataListe()
    )

fun mockInntektsmeldingHaandtertDto(dokumentId: UUID? = MockUuid.inntektsmeldingId): InntektsmeldingHaandtertDto =
    InntektsmeldingHaandtertDto(
        orgnr = "287429436".let(::Orgnr),
        fnr = "fnr",
        vedtaksperiodeId = MockUuid.vedtaksperiodeId,
        inntektsmeldingId = dokumentId,
        haandtert = LocalDateTime.MAX
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
