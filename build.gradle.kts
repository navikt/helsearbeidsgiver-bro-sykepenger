import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val mainClassPath = "no.nav.helsearbeidsgiver.bro.sykepenger.AppKt"

plugins {
    application
    kotlin("jvm")
    id("org.jmailen.kotlinter")
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
    val logbackVersion: String by project
    val slf4jVersion: String by project
    val rapidsAndRiversVersion: String by project
    val hikariVersion: String by project
    val flywayCoreVersion: String by project
    val testcontainersPostgresqlVersion: String by project
    val junitJupiterVersion: String by project
    val kotliqueryVersion: String by project
    val postgresqlVersion: String by project

    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")

}
