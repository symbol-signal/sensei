plugins {
    kotlin("jvm") version "2.2.10"
}

group = "symsig"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")
    implementation("de.kempmobil.ktor.mqtt:mqtt-core:0.7.0")
    implementation("de.kempmobil.ktor.mqtt:mqtt-client:0.7.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}