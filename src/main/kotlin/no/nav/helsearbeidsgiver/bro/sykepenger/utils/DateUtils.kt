package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun LocalDate.isDayBefore(other: LocalDate): Boolean = this == other.minusDays(1)

fun LocalDateTime.truncMillis(): LocalDateTime = truncatedTo(ChronoUnit.MILLIS)
