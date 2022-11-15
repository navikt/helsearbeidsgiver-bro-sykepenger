package no.nav.helsearbeidsgiver.bro.sykepenger

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun LocalDateTime.truncMillis(): LocalDateTime =
    truncatedTo(ChronoUnit.MILLIS)
