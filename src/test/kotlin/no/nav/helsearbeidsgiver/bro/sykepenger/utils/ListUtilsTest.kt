package no.nav.helsearbeidsgiver.bro.sykepenger.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ListUtilsTest :
    FunSpec({
        context(List<Any>::leadingAndLast.name) {
            test("deler liste i ledende og siste element") {
                val numbers = listOf(1, 2, 3, 4)
                val splitNumbers = numbers.leadingAndLast()

                splitNumbers.shouldNotBeNull()
                splitNumbers.first shouldBe listOf(1, 2, 3)
                splitNumbers.second shouldBe 4
            }

            test("liste med enkelt element prioriterer siste over ledende") {
                val numbers = listOf(101)
                val splitNumbers = numbers.leadingAndLast()

                splitNumbers.shouldNotBeNull()
                splitNumbers.first.shouldBeEmpty()
                splitNumbers.second shouldBe 101
            }

            test("tom liste gir 'null'") {
                emptyList<Int>().leadingAndLast().shouldBeNull()
            }
        }

        context(List<Any>::zipWithNextOrNull.name) {
            test("zipper element sammen med neste eller null ved ingen neste") {
                val numbers = listOf(2, 4, 6, 8)

                numbers.zipWithNextOrNull() shouldBe
                    listOf(
                        2 to 4,
                        4 to 6,
                        6 to 8,
                        8 to null,
                    )
            }

            test("tom liste gir tom liste") {
                emptyList<Int>().zipWithNextOrNull().shouldBeEmpty()
            }
        }
    })
