plugins {
    kotlin("jvm") version "2.2.10"
    application
    kotlin("plugin.serialization") version "2.2.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "symsig"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "3.2.3"  // Should be kept in sync with the version used by the mqtt lib

dependencies {
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.19")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("de.kempmobil.ktor.mqtt:mqtt-core:0.8.1")
    implementation("de.kempmobil.ktor.mqtt:mqtt-client:0.8.1")
    implementation("io.ktor:ktor-client-cio:${ktorVersion}")

    // Kotlin scripting (for dynamic rule loading)
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:2.2.10")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:2.2.10")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:2.2.10")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    implementation(kotlin("stdlib-jdk8"))
}

application {
    mainClass.set("symsig.sensei.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<JavaCompile> {
    options.release.set(17)
}