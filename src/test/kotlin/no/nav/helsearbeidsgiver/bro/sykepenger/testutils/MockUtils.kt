package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ArbeidsgiverPeriode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.FastsattInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Periode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.toJson
import java.util.UUID

object MockUuid {
    const val STRING = "01234567-abcd-0123-abcd-012345678901"
    val uuid = STRING.let(UUID::fromString).shouldNotBeNull()
}

fun mockForespoerselDto(): ForespoerselDto =
    ForespoerselDto(
        orgnr = "12345678901",
        fnr = "123456789",
        vedtaksperiodeId = MockUuid.uuid,
        fom = 1.januar,
        tom = 16.januar,
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
        orgnr = "123",
        fnr = "abc",
        vedtaksperiodeId = MockUuid.uuid
    )

fun mockForespoerselSvar(): ForespoerselSvar =
    ForespoerselSvar(
        orgnr = "123",
        fnr = "abc",
        vedtaksperiodeId = MockUuid.uuid,
        fom = 1.januar,
        tom = 16.januar,
        forespurtData = mockForespurtDataListe(),
        boomerang = mapOf(
            "boom" to "shakalaka".toJson()
        )
    )
