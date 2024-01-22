plugins {
    kotlin("jvm")
}

group = "app.phtn.pulse"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
}

kotlin {
    jvmToolchain(18)
}