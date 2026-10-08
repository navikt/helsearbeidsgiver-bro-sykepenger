val mainClassPath = "no.nav.helsearbeidsgiver.bro.sykepenger.AppKt"

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.kotest")
    id("org.jmailen.kotlinter")
}

application {
    mainClass.set(mainClassPath)
}

kotlin {
    jvmToolchain(25)
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
            attributes["Class-Path"] =
                configurations
                    .runtimeClasspath
                    .get()
                    .joinToString(separator = " ") { it.name }
        }
        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file =
                    layout
                        .buildDirectory
                        .file("libs/${it.name}")
                        .get()
                        .asFile
                if (!file.exists()) {
                    it.copyTo(file)
                }
            }
        }
    }
}

repositories {
    val githubPassword = project.property("githubPassword") as String

    mavenCentral()
    maven {
        setUrl("https://maven.pkg.github.com/navikt/*")
        credentials {
            username = "x-access-token"
            password = githubPassword
        }
    }
}

dependencies {
    val exposedVersion = project.property("exposedVersion") as String
    val flywayCoreVersion = project.property("flywayCoreVersion") as String
    val hagDomeneInntektsmeldingVersion = project.property("hagDomeneInntektsmeldingVersion") as String
    val hikariVersion = project.property("hikariVersion") as String
    val kafkaClientVersion = project.property("kafkaClientVersion") as String
    val kotestVersion = project.property("kotestVersion") as String
    val kotlinxSerializationVersion = project.property("kotlinxSerializationVersion") as String
    val logbackVersion = project.property("logbackVersion") as String
    val mockkVersion = project.property("mockkVersion") as String
    val postgresqlVersion = project.property("postgresqlVersion") as String
    val rapidsAndRiversVersion = project.property("rapidsAndRiversVersion") as String
    val testcontainersVersion = project.property("testcontainersVersion") as String
    val utilsVersion = project.property("utilsVersion") as String

    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("no.nav.helsearbeidsgiver:domene-inntektsmelding:$hagDomeneInntektsmeldingVersion")
    implementation("no.nav.helsearbeidsgiver:utils:$utilsVersion")
    implementation("org.apache.kafka:kafka-clients:$kafkaClientVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")

    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    runtimeOnly("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")

    testImplementation(testFixtures("no.nav.helsearbeidsgiver:utils:$utilsVersion"))
    testImplementation("com.github.navikt.rapids-and-rivers:rapids-and-rivers-test:$rapidsAndRiversVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-framework-engine:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
}
