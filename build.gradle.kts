plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "symsig"
version = "0.1.0-SNAPSHOT"

val ktorVersion = "2.3.12"

repositories {
    mavenCentral()
}

dependencies {
    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    // Ktor framework for..
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    // .. Websocket server
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    // .. HTTP client
    implementation("io.ktor:ktor-client:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    // Ktor JSON deserialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.github.nomisrev:kotlinx-serialization-jsonpath:1.0.0")

    // Testing
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

application {
    mainClass.set("symsig.sensei.SenseiAppKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(16)
}