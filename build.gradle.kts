import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val mainClassPath = "no.nav.helsearbeidsgiver.bro.sykepenger.AppKt"

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jmailen.kotlinter")
}

application {
    mainClass.set(mainClassPath)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    named<Jar>("jar") {
        archiveBaseName.set("app")
        manifest {
            attributes["Main-Class"] = mainClassPath
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }
        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = layout.buildDirectory.file("libs/${it.name}").get().asFile
                if (!file.exists()) {
                    it.copyTo(file)
                }
            }
        }
    }
}

repositories {
    val githubPassword: String by project

    mavenCentral()
    google()
    maven("https://packages.confluent.io/maven/")
    maven {
        setUrl("https://maven.pkg.github.com/navikt/*")
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
    }
}

dependencies {
    val exposedVersion: String by project
    val flywayCoreVersion: String by project
    val hikariVersion: String by project
    val kotestVersion: String by project
    val kotlinxSerializationVersion: String by project
    val logbackVersion: String by project
    val mockkVersion: String by project
    val postgresqlVersion: String by project
    val rapidsAndRiversVersion: String by project
    val slf4jVersion: String by project
    val testcontainersPostgresqlVersion: String by project
    val utilsVersion: String by project
    val ktorVersion: String by project
    val tokenSupportVersion: String by project
    val tokenProviderVersion: String by project

    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("no.nav.helsearbeidsgiver:tokenprovider:$tokenProviderVersion")
    implementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")
    implementation("no.nav.security:token-client-core:$tokenSupportVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-framework-datatest:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
}
