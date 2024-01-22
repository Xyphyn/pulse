plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "pulse"
include("game")
include("lobby")
include("main")
include("common")
