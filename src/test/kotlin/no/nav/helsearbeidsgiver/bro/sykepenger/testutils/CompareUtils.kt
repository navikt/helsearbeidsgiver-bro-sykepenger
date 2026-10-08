package no.nav.helsearbeidsgiver.bro.sykepenger.testutils

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import no.nav.helsearbeidsgiver.bro.sykepenger.domene.ForespoerselDto
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun ForespoerselDto.shouldBeEqualToWithApproximateDateTime(other: ForespoerselDto) {
    shouldBeEqualToIgnoringFields(
        other,
        other::opprettet,
        other::oppdatert,
        other::forrigeKontaktFraSpleis,
    )

    listOf(
        opprettet to other.opprettet,
        oppdatert to other.oppdatert,
        forrigeKontaktFraSpleis to other.forrigeKontaktFraSpleis,
    ).forEach {
        it.first.shouldBeWithin(1.seconds.toJavaDuration(), it.second)
    }
}
