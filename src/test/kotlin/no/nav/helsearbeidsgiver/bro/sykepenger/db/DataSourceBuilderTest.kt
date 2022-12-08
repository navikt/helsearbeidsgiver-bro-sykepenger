package no.nav.helsearbeidsgiver.bro.sykepenger.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec

class DataSourceBuilderTest : FunSpec({
    test("kaster ikke exception når tilkobling konfigureres riktig") {
        shouldNotThrowAny {
            DataSourceBuilder(
                mapOf(
                    DataSourceConfig.HOST to "foobar",
                    DataSourceConfig.PORT to "foobar",
                    DataSourceConfig.DATABASE to "foobar",
                    DataSourceConfig.USERNAME to "foobar",
                    DataSourceConfig.PASSWORD to "foobar"
                )
            )
        }
    }

    test("kaster exception ved manglende konfig") {
        shouldThrowExactly<IllegalArgumentException> {
            DataSourceBuilder(emptyMap())
        }

        shouldThrowExactly<IllegalArgumentException> {
            DataSourceBuilder(
                mapOf(
                    DataSourceConfig.HOST to "foobar"
                )
            )
        }

        shouldThrowExactly<IllegalArgumentException> {
            DataSourceBuilder(
                mapOf(
                    DataSourceConfig.HOST to "foobar",
                    DataSourceConfig.PORT to "foobar"
                )
            )
        }

        shouldThrowExactly<IllegalArgumentException> {
            DataSourceBuilder(
                mapOf(
                    DataSourceConfig.HOST to "foobar",
                    DataSourceConfig.PORT to "foobar",
                    DataSourceConfig.DATABASE to "foobar"
                )
            )
        }

        shouldThrowExactly<IllegalArgumentException> {
            DataSourceBuilder(
                mapOf(
                    DataSourceConfig.HOST to "foobar",
                    DataSourceConfig.PORT to "foobar",
                    DataSourceConfig.DATABASE to "foobar",
                    DataSourceConfig.USERNAME to "foobar"
                )
            )
        }
    }
})
