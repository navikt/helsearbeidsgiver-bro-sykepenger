package no.nav.helsearbeidsgiver.bro.sykepenger.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import no.nav.helsearbeidsgiver.bro.sykepenger.testutils.mockForespoerselDto
import no.nav.helsearbeidsgiver.utils.test.date.september
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ForespoerselDtoTest :
    FunSpec({
        context("erDuplikat") {
            withData(
                mapOf<String, (ForespoerselDto) -> ForespoerselDto>(
                    "Aksepterer helt like" to { it },
                    "Ignorerer 'forespoerselId'" to { it.copy(forespoerselId = UUID.randomUUID()) },
                    "Ignorerer 'opprettet'" to { it.copy(opprettet = LocalDateTime.now().minusDays(5)) },
                    "Ignorerer 'oppdatert'" to { it.copy(oppdatert = LocalDateTime.now().plusDays(10)) },
                ),
            ) { endreFn ->
                val original = mockForespoerselDto()

                val endret = endreFn(original)

                original.erDuplikatAv(endret).shouldBeTrue()
            }

            withData(
                mapOf<String, (ForespoerselDto) -> ForespoerselDto>(
                    "Oppdager ulik 'type'" to { it.copy(type = Type.BEGRENSET) },
                    "Oppdager ulik 'status'" to { it.copy(status = Status.FORKASTET) },
                    "Oppdager ulik 'orgnr'" to { it.copy(orgnr = "999777555".let(::Orgnr)) },
                    "Oppdager ulik 'fnr'" to { it.copy(fnr = "22244466688") },
                    "Oppdager ulik 'vedtaksperiodeId'" to { it.copy(vedtaksperiodeId = UUID.randomUUID()) },
                    "Oppdager ulik 'egenmeldingsperioder'" to {
                        it.copy(egenmeldingsperioder = listOf(Periode(2.september(1774), 4.september(1774))))
                    },
                    "Oppdager ulik 'sykmeldingsperioder'" to {
                        it.copy(sykmeldingsperioder = listOf(Periode(22.september(1774), 24.september(1774))))
                    },
                    "Oppdager ulik 'bestemmendeFravaersdager'" to {
                        it.copy(
                            bestemmendeFravaersdager = mapOf(it.orgnr to LocalDate.now().plusDays(7)),
                        )
                    },
                    "Oppdager ulik 'forespurtData'" to {
                        it.copy(
                            forespurtData =
                                listOf(
                                    SpleisInntekt(
                                        forslag =
                                            SpleisForslagInntekt(
                                                forrigeInntekt =
                                                    SpleisForrigeInntekt(
                                                        skjæringstidspunkt = LocalDate.now().minusDays(62),
                                                        kilde = "Farris",
                                                        beløp = 12.1,
                                                    ),
                                            ),
                                    ),
                                ),
                        )
                    },
                ),
            ) { endreFn ->
                val original = mockForespoerselDto()

                val endret = endreFn(original)

                original.erDuplikatAv(endret).shouldBeFalse()
            }
        }
    })
