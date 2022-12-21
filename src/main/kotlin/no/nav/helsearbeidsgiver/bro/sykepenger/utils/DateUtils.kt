package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun LocalDateTime.truncMillis(): LocalDateTime =
    truncatedTo(ChronoUnit.MILLIS)
