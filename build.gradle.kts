plugins {
    kotlin("jvm") version "1.9.21"
    application
}

version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":game"))
    implementation(project(":lobby"))
    implementation(project(":common"))
}

allprojects {
    apply(plugin = "kotlin")

    repositories {
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
    }

    dependencies {
        implementation("dev.hollowcube:minestom-ce:5347c0b11f")
        implementation("dev.hollowcube:polar:1.6.3")
        implementation("com.github.EmortalMC:Rayfast:7975ac5e4c7")
        implementation("net.kyori:adventure-text-minimessage:4.14.0")
        implementation("com.github.emortalmc:NBStom:d8fc17002c")
    }

    kotlin {
        jvmToolchain(18)
    }
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