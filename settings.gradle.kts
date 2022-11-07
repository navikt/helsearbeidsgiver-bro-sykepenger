rootProject.name = "helsearbeidsgiver-bro-sykepenger"

pluginManagement {
    plugins {
        val kotlinVersion: String by settings
        val kotlinterVersion: String by settings

        kotlin("jvm") version kotlinVersion
        id("org.jmailen.kotlinter") version kotlinterVersion
        kotlin("plugin.serialization") version kotlinVersion
    }
}
