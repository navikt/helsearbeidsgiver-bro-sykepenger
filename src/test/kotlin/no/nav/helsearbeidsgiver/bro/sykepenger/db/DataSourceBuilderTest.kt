package no.nav.helsearbeidsgiver.bro.sykepenger.db

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DataSourceBuilderTest {
    @Test
    fun `kaster ikke exception når tilkobling konfigureres riktig`() {
        assertDoesNotThrow {
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

    @Test
    fun `kaster exception ved manglende konfig`() {
        assertThrows<IllegalArgumentException> {
            DataSourceBuilder(emptyMap())
        }

        assertThrows<IllegalArgumentException> {
            DataSourceBuilder(
                mapOf(
                    DataSourceConfig.HOST to "foobar"
                )
            )
        }

        assertThrows<IllegalArgumentException> {
            DataSourceBuilder(
                mapOf(
                    DataSourceConfig.HOST to "foobar",
                    DataSourceConfig.PORT to "foobar"
                )
            )
        }

        assertThrows<IllegalArgumentException> {
            DataSourceBuilder(
                mapOf(
                    DataSourceConfig.HOST to "foobar",
                    DataSourceConfig.PORT to "foobar",
                    DataSourceConfig.DATABASE to "foobar"
                )
            )
        }

        assertThrows<IllegalArgumentException> {
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
}
