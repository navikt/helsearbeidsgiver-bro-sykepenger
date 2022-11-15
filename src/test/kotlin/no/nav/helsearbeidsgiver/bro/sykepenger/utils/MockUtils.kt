package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import no.nav.helsearbeidsgiver.bro.sykepenger.ArbeidsgiverPeriode
import no.nav.helsearbeidsgiver.bro.sykepenger.ForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.Forslag
import no.nav.helsearbeidsgiver.bro.sykepenger.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.Refusjon

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
