rootProject.name = "helsearbeidsgiver-bro-sykepenger"

pluginManagement {
    plugins {
        val kotestVersion: String by settings
        val kotlinVersion: String by settings
        val kotlinterVersion: String by settings

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("io.kotest") version kotestVersion
        id("org.jmailen.kotlinter") version kotlinterVersion
    }
}
