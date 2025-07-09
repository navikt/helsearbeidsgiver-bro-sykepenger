package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun LocalDate.isDayBefore(other: LocalDate): Boolean = plusDays(1).isEqual(other)

fun LocalDateTime.truncMillis(): LocalDateTime = truncatedTo(ChronoUnit.MILLIS)
