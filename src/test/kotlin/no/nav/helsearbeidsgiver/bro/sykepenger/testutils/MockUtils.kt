package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDtoMedEksponertFsp
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSimba
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtData
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.InntektsmeldingHaandtertDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisArbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Type
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDateTime
import java.util.UUID

object MockUuid {
    val vedtaksperiodeId: UUID = "01234567-abcd-0123-abcd-012345678901".let(UUID::fromString)
    val inntektsmeldingId: UUID = "22efb342-3e72-4880-a449-eb1efcf0f18b".let(UUID::fromString)
}

fun mockForespoerselDto(): ForespoerselDto {
    val orgnr = Orgnr.genererGyldig()

    return ForespoerselDto(
        forespoerselId = UUID.randomUUID(),
        vedtaksperiodeId = MockUuid.vedtaksperiodeId,
        type = Type.KOMPLETT,
        status = Status.AKTIV,
        orgnr = orgnr,
        fnr = Fnr.genererGyldig(),
        egenmeldingsperioder = listOf(Periode(1.januar, 1.januar)),
        sykmeldingsperioder =
            listOf(
                Periode(2.januar, 10.januar),
                Periode(15.januar, 20.januar),
            ),
        bestemmendeFravaersdager =
            mapOf(
                orgnr to 15.januar,
                Orgnr.genererGyldig() to 17.januar,
                Orgnr.genererGyldig() to 19.januar,
            ),
        forespurtData = mockSpleisForespurtDataListe(),
    )
}

fun mockForespoerselDtoMedEksponertFsp(vedtaksperiodeId: UUID = MockUuid.vedtaksperiodeId): ForespoerselDtoMedEksponertFsp {
    val orgnr = Orgnr.genererGyldig()

    return ForespoerselDtoMedEksponertFsp(
        forespoerselId = UUID.randomUUID(),
        type = Type.KOMPLETT,
        status = Status.AKTIV,
        orgnr = orgnr,
        fnr = Fnr.genererGyldig(),
        vedtaksperiodeId = vedtaksperiodeId,
        egenmeldingsperioder = listOf(Periode(1.januar, 1.januar)),
        sykmeldingsperioder =
            listOf(
                Periode(2.januar, 10.januar),
                Periode(15.januar, 20.januar),
            ),
        bestemmendeFravaersdager =
            mapOf(
                orgnr to 15.januar,
                Orgnr.genererGyldig() to 17.januar,
                Orgnr.genererGyldig() to 19.januar,
            ),
        forespurtData = mockSpleisForespurtDataListe(),
        eksponertForespoerselId = UUID.randomUUID(),
    )
}

fun mockSpleisForespurtDataListe(): List<SpleisForespurtDataDto> =
    listOf(
        SpleisArbeidsgiverperiode,
        SpleisInntekt,
        SpleisRefusjon,
    )

fun mockForespoerselSvarSuksess(): ForespoerselSimba {
    val orgnr = Orgnr.genererGyldig()

    return ForespoerselSimba(
        orgnr = orgnr,
        fnr = Fnr.genererGyldig(),
        forespoerselId = UUID.randomUUID(),
        vedtaksperiodeId = UUID.randomUUID(),
        egenmeldingsperioder = listOf(Periode(1.januar, 1.januar)),
        sykmeldingsperioder = listOf(Periode(2.januar, 16.januar)),
        bestemmendeFravaersdager = mapOf(orgnr to 10.november(1999)),
        forespurtData = mockForespurtData(),
        erBesvart = false,
        erBegrenset = false,
        opprettet = LocalDateTime.now(),
    )
}

fun mockForespurtData(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode = Arbeidsgiverperiode(paakrevd = true),
        inntekt = Inntekt(paakrevd = true),
        refusjon = Refusjon(paakrevd = true),
    )

fun mockInntektsmeldingHaandtertDto(dokumentId: UUID? = MockUuid.inntektsmeldingId): InntektsmeldingHaandtertDto =
    InntektsmeldingHaandtertDto(
        orgnr = Orgnr.genererGyldig(),
        fnr = Fnr.genererGyldig(),
        vedtaksperiodeId = MockUuid.vedtaksperiodeId,
        inntektsmeldingId = dokumentId,
        haandtert = LocalDateTime.MAX,
    )

fun mockJsonElement(): JsonElement = """{"aTestKey":"aTestValue"}""".parseJson()
