package no.nav.helsearbeidsgiver.bro.sykepenger.utils

fun Boolean.ifTrue(block: () -> Unit): Boolean =
    also { if (this) block() }

fun Boolean.ifFalse(block: () -> Unit): Boolean =
    also { if (!this) block() }
