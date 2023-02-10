package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import java.util.UUID

/** Brukes for å kunne mocke UUID-en i tester. */
fun randomUuid(): UUID =
    UUID.randomUUID()
