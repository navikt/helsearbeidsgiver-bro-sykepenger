package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ArbeidsgiverPeriode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.FastsattInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson
import java.util.UUID

object MockUuid {
    val vedtaksperiodeId = UUID.fromString("01234567-abcd-0123-abcd-012345678901")
    val forespoerselId = UUID.fromString("98765654-abcd-0123-abcd-012345678901")
}

fun mockForespoerselDto(): ForespoerselDto =
    ForespoerselDto(
        forespoerselId = MockUuid.forespoerselId,
        orgnr = "12345678901",
        fnr = "123456789",
        vedtaksperiodeId = MockUuid.vedtaksperiodeId,
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
        Refusjon
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
        Refusjon
    )

fun mockForespoerselMottatt(): ForespoerselMottatt =
    ForespoerselMottatt(
        forespoerselId = MockUuid.forespoerselId,
        orgnr = "123",
        fnr = "abc"
    )

fun mockForespoerselSvar(): ForespoerselSvar =
    ForespoerselSvar(
        forespoerselId = MockUuid.vedtaksperiodeId,
        orgnr = "123",
        fnr = "abc",
        sykmeldingsperioder = listOf(Periode(1.januar, 16.januar)),
        forespurtData = mockForespurtDataListe(),
        boomerang = mapOf(
            "boom" to "shakalaka".toJson()
        )
    )
