package no.nav.helsearbeidsgiver.bro.sykepenger

import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespurtData
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Inntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.Refusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisArbeidsgiverperiode
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisFastsattInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForespurtDataDto
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisForslagRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisInntekt
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.SpleisRefusjon
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.Loggernaut
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.isDayBefore
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.leadingAndLast
import no.nav.helsearbeidsgiver.bro.sykepenger.utils.mapWithNext

private val loggernaut = Loggernaut(ForespurtData)

fun List<SpleisForespurtDataDto>.tilForespurtData(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode = lesArbeidsgiverperiode(),
        inntekt = lesInntekt(),
        refusjon = lesRefusjon()
    )

private fun List<SpleisForespurtDataDto>.lesArbeidsgiverperiode(): Arbeidsgiverperiode =
    Arbeidsgiverperiode(
        paakrevd = contains(SpleisArbeidsgiverperiode)
    )

private fun List<SpleisForespurtDataDto>.lesInntekt(): Inntekt {
    val inntekt = filterIsInstance<SpleisInntekt>()
        .firstOrNull()
        ?.let {
            Inntekt(
                paakrevd = true,
                forslag = ForslagInntekt.Grunnlag(
                    beregningsmaaneder = it.forslag.beregningsmåneder
                )
            )
        }

    val fastsattInntekt = filterIsInstance<SpleisFastsattInntekt>()
        .firstOrNull()
        ?.let {
            Inntekt(
                paakrevd = false,
                forslag = ForslagInntekt.Fastsatt(
                    fastsattInntekt = it.fastsattInntekt
                )
            )
        }

    return inntekt
        ?: fastsattInntekt
        ?: throw IllegalArgumentException("Liste med forespurt data fra Spleis må innholde minst én form for inntekt.")
}

private fun List<SpleisForespurtDataDto>.lesRefusjon(): Refusjon =
    filterIsInstance<SpleisRefusjon>()
        .firstOrNull()
        ?.let {
            Refusjon(
                paakrevd = true,
                forslag = it.forslag.tilForslagRefusjon()
            )
        }
        ?: Refusjon.ikkePaakrevd()

private fun List<SpleisForslagRefusjon>.tilForslagRefusjon(): ForslagRefusjon =
    sortedBy { it.fom }
        .medEksplisitteRefusjonsopphold()
        .leadingAndLast()
        ?.let { (leading, last) ->
            ForslagRefusjon(
                perioder = leading.plus(last).map {
                    ForslagRefusjon.Periode(it.fom, it.beløp)
                },
                opphoersdato = last.tom
            )
        }
        ?: ForslagRefusjon(emptyList(), null)

private fun List<SpleisForslagRefusjon>.medEksplisitteRefusjonsopphold(): List<SpleisForslagRefusjon> {
    val nyRefusjonsforslag = mapWithNext { current, next ->
        if (next == null || current.tom == null || current.tom.isDayBefore(next.fom)) {
            listOf(current)
        } else {
            listOf(
                current,
                SpleisForslagRefusjon(
                    fom = current.tom.plusDays(1),
                    tom = next.fom.minusDays(1),
                    beløp = 0.0
                )
            )
        }
    }
        .flatten()

    if (isNotEmpty() && size != nyRefusjonsforslag.size) {
        loggernaut.sikker.info("Refusjonsforslag endret fra $this til $nyRefusjonsforslag.")
    }

    return nyRefusjonsforslag
}
