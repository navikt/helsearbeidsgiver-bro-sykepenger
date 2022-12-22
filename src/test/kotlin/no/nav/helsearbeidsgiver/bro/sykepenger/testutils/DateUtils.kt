package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

private val defaultAar = 2018

val Int.januar get() =
    this.januar(defaultAar)

fun Int.januar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JANUARY, this)

fun januar(aar: Int): YearMonth =
    YearMonth.of(aar, Month.JANUARY)

val Int.februar get() =
    this.februar(defaultAar)

fun Int.februar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.FEBRUARY, this)

fun februar(aar: Int): YearMonth =
    YearMonth.of(aar, Month.FEBRUARY)

val Int.mars get() =
    this.mars(defaultAar)

fun Int.mars(aar: Int): LocalDate =
    LocalDate.of(aar, Month.MARCH, this)

fun mars(aar: Int): YearMonth =
    YearMonth.of(aar, Month.MARCH)

val Int.april get() =
    this.april(defaultAar)

fun Int.april(aar: Int): LocalDate =
    LocalDate.of(aar, Month.APRIL, this)

fun april(aar: Int): YearMonth =
    YearMonth.of(aar, Month.APRIL)

val Int.mai get() =
    this.mai(defaultAar)

fun Int.mai(aar: Int): LocalDate =
    LocalDate.of(aar, Month.MAY, this)

fun mai(aar: Int): YearMonth =
    YearMonth.of(aar, Month.MAY)

val Int.juni get() =
    this.juni(defaultAar)

fun Int.juni(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JUNE, this)

fun juni(aar: Int): YearMonth =
    YearMonth.of(aar, Month.JUNE)

val Int.juli get() =
    this.juli(defaultAar)

fun Int.juli(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JULY, this)

fun juli(aar: Int): YearMonth =
    YearMonth.of(aar, Month.JULY)

val Int.august get() =
    this.august(defaultAar)

fun Int.august(aar: Int): LocalDate =
    LocalDate.of(aar, Month.AUGUST, this)

fun august(aar: Int): YearMonth =
    YearMonth.of(aar, Month.AUGUST)

val Int.september get() =
    this.september(defaultAar)

fun Int.september(aar: Int): LocalDate =
    LocalDate.of(aar, Month.SEPTEMBER, this)

fun september(aar: Int): YearMonth =
    YearMonth.of(aar, Month.SEPTEMBER)

val Int.oktober get() =
    this.oktober(defaultAar)

fun Int.oktober(aar: Int): LocalDate =
    LocalDate.of(aar, Month.OCTOBER, this)

fun oktober(aar: Int): YearMonth =
    YearMonth.of(aar, Month.OCTOBER)

val Int.november get() =
    this.november(defaultAar)

fun Int.november(aar: Int): LocalDate =
    LocalDate.of(aar, Month.NOVEMBER, this)

fun november(aar: Int): YearMonth =
    YearMonth.of(aar, Month.NOVEMBER)

val Int.desember get() =
    this.desember(defaultAar)

fun Int.desember(aar: Int): LocalDate =
    LocalDate.of(aar, Month.DECEMBER, this)

fun desember(aar: Int): YearMonth =
    YearMonth.of(aar, Month.DECEMBER)
