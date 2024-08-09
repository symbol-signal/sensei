plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "symsig"
version = "0.1.0-SNAPSHOT"

val ktor_version = "2.3.12"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
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