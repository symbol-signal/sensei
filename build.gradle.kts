plugins {
    kotlin("jvm") version "2.2.10"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "symsig"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")
    implementation("de.kempmobil.ktor.mqtt:mqtt-core:0.7.0")
    implementation("de.kempmobil.ktor.mqtt:mqtt-client:0.7.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("symsig.sensei.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}