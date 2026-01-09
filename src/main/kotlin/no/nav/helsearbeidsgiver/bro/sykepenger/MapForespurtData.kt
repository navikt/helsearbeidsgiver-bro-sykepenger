package no.nav.helsearbeidsgiver.bro.sykepenger

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtData
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisArbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisRefusjon

fun List<SpleisForespurtDataDto>.tilForespurtData(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode =
            Arbeidsgiverperiode(
                paakrevd = contains(SpleisArbeidsgiverperiode),
            ),
        inntekt =
            Inntekt(
                paakrevd = contains(SpleisInntekt),
            ),
        refusjon =
            Refusjon(
                paakrevd = contains(SpleisRefusjon),
            ),
    )
