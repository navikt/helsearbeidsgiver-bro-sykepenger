rootProject.name = "helsearbeidsgiver-bro-sykepenger"

pluginManagement {
    plugins {
        val kotestVersion = providers.gradleProperty("kotestVersion").get()
        val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
        val kotlinterVersion = providers.gradleProperty("kotlinterVersion").get()

        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("io.kotest") version kotestVersion
        id("org.jmailen.kotlinter") version kotlinterVersion
    }
}
