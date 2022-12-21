package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ArbeidsgiverPeriode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselMottatt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Forslag
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Status
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
                Forslag(
                    fom = 1.januar,
                    tom = 10.januar
                ),
                Forslag(
                    fom = 15.januar,
                    tom = 20.januar
                )
            )
        ),
        Refusjon,
        Inntekt
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
        forespurtData = mockForespurtDataListe()
    )
