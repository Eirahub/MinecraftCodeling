pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads the Java 25 and 21 toolchains when they are not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // One source tree, several Minecraft versions (issue #189): see stonecutter.gradle.kts and README "Build".
    id("dev.kikugie.stonecutter") version "0.10"
    // Picks Fabric Loom per version: net.fabricmc.fabric-loom on 26.x (no obfuscation), fabric-loom-remap before.
    id("dev.kikugie.loom-back-compat") version "0.4.3"
}

rootProject.name = "codeling"

// core is pure Java and the same for every Minecraft version, so it stays outside Stonecutter.
include("core")

// common, fabric and neoforge get one Gradle project per Minecraft version: :fabric:26.3, :fabric:1.21.1 and so on.
// The project for a version lives in <module>/versions/<version>/ (its gradle.properties and build output) and runs
// the module's build.gradle.kts. 26.3 is the active version: its code is what src/ holds and what git stores.
stonecutter {
    create(rootProject) {
        branch("common") { versions("26.3", "26.2", "26.1.2", "1.21.11", "1.21.10", "1.21.1") }
        branch("fabric") { versions("26.3", "26.2", "26.1.2", "1.21.11", "1.21.10", "1.21.1") }
        branch("neoforge") { versions("26.3", "26.2", "26.1.2", "1.21.11", "1.21.10", "1.21.1") }
        vcsVersion = "26.3"
    }
}
