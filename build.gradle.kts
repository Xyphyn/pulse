plugins {
    kotlin("jvm") version "1.9.21"
    application
}

version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation("dev.hollowcube:minestom-ce:1554487748")
    implementation("dev.hollowcube:polar:1.6.3")
    implementation("com.github.EmortalMC:Rayfast:7975ac5e4c7")
    implementation("net.kyori:adventure-text-minimessage:4.14.0")
}

kotlin {
    jvmToolchain(18)
}

application {
    mainClass.set("app.phtn.pulse.MainKt")
}

sourceSets {
    main {
        java.srcDirs("src/main/kotlin")
        kotlin.srcDirs("src/main/kotlin")
    }
}