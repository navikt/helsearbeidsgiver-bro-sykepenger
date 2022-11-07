package no.nav.helsearbeidsgiver.bro.sykepenger

import java.time.LocalDate
import java.time.Month

private val defaultAar = 2018

val Int.januar get() =
    this.januar(defaultAar)

fun Int.januar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JANUARY, this)

val Int.februar get() =
    this.februar(defaultAar)

fun Int.februar(aar: Int): LocalDate =
    LocalDate.of(aar, Month.FEBRUARY, this)

val Int.mars get() =
    this.mars(defaultAar)

fun Int.mars(aar: Int): LocalDate =
    LocalDate.of(aar, Month.MARCH, this)

val Int.april get() =
    this.april(defaultAar)

fun Int.april(aar: Int): LocalDate =
    LocalDate.of(aar, Month.APRIL, this)

val Int.mai get() =
    this.mai(defaultAar)

fun Int.mai(aar: Int): LocalDate =
    LocalDate.of(aar, Month.MAY, this)

val Int.juni get() =
    this.juni(defaultAar)

fun Int.juni(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JUNE, this)

val Int.juli get() =
    this.juli(defaultAar)

fun Int.juli(aar: Int): LocalDate =
    LocalDate.of(aar, Month.JULY, this)

val Int.august get() =
    this.august(defaultAar)

fun Int.august(aar: Int): LocalDate =
    LocalDate.of(aar, Month.AUGUST, this)

val Int.september get() =
    this.september(defaultAar)

fun Int.september(aar: Int): LocalDate =
    LocalDate.of(aar, Month.SEPTEMBER, this)

val Int.oktober get() =
    this.oktober(defaultAar)

fun Int.oktober(aar: Int): LocalDate =
    LocalDate.of(aar, Month.OCTOBER, this)

val Int.november get() =
    this.november(defaultAar)

fun Int.november(aar: Int): LocalDate =
    LocalDate.of(aar, Month.NOVEMBER, this)

val Int.desember get() =
    this.desember(defaultAar)

fun Int.desember(aar: Int): LocalDate =
    LocalDate.of(aar, Month.DECEMBER, this)
