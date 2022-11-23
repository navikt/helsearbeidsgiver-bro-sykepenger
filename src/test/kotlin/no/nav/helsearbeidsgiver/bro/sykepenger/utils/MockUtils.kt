package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import no.nav.helsearbeidsgiver.bro.sykepenger.ArbeidsgiverPeriode
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespoerselDto
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.Forslag
import no.nav.helsearbeidsgiver.bro.sykepenger.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.Status
import java.util.UUID

const val MOCK_UUID = "01234567-abcd-0123-abcd-012345678901"

fun mockForespoerselDto(): ForespoerselDto =
    ForespoerselDto(
        orgnr = "12345678901",
        fnr = "123456789",
        vedtaksperiodeId = UUID.fromString(MOCK_UUID),
        fom = 1.januar,
        tom = 16.januar,
        forespurtData = mockForespurtDataListe(),
        forespoerselBesvart = null,
        status = Status.TRENGER_OPPLYSNINGER_FRA_ARBEIDSGIVER
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
