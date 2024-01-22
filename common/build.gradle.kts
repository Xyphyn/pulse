plugins {
    kotlin("jvm")
}

group = "app.phtn.pulse"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(18)
}