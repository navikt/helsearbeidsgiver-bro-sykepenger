import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val mainClassPath = "no.nav.helsearbeidsgiver.bro.sykepenger.AppKt"

plugins {
    application
    kotlin("jvm")
    id("org.jmailen.kotlinter")
    kotlin("plugin.serialization")
}

application {
    mainClass.set(mainClassPath)
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

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
                val file = File("$buildDir/libs/${it.name}")
                if (!file.exists()) {
                    it.copyTo(file)
                }
            }
        }
    }

    test {
        environment(
            "KAFKA_BROKERS" to "http://localhost:8080",
            "KAFKA_KEYSTORE_PATH" to "mockKeystorePath",
            "KAFKA_TRUSTSTORE_PATH" to "mockTruststorePath",
            "KAFKA_CREDSTORE_PASSWORD" to "mockCredstorePassword"
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    val githubPassword: String by project

    mavenCentral()
    google()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io") {
        content {
            excludeGroup("no.nav.helsearbeidsgiver")
        }
    }
    maven {
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/*")
    }
}

dependencies {
    val flywayCoreVersion: String by project
    val hikariVersion: String by project
    val junitJupiterVersion: String by project
    val kotestVersion: String by project
    val kotlinSerializationVersion: String by project
    val kotliqueryVersion: String by project
    val logbackVersion: String by project
    val mockkVersion: String by project
    val postgresqlVersion: String by project
    val rapidsAndRiversVersion: String by project
    val slf4jVersion: String by project
    val testcontainersPostgresqlVersion: String by project

    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}
