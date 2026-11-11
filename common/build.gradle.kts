// Vanilla Minecraft code shared by every loader (issue #186): robots, relay handler, payload types, client screens,
// renderer and the camera mixins. Compiled against plain vanilla Minecraft with ModDevGradle in NeoForm mode, as
// jaredlll08's MultiLoader-Template does, so no loader API is on the classpath. This module builds no mod jar: each
// loader module compiles these sources into its own jar (see fabric/build.gradle.kts). checkCommonImports fails on a
// loader import. Two source sets as in :fabric (Loom's split environment): client code may use main, not the reverse.
// Stonecutter (issue #189) runs this script once per Minecraft version (:common:26.3, :common:1.21.1); the version's
// own values (NeoForm, Java) are in versions/<version>/gradle.properties.
plugins {
    `java-library`
    id("net.neoforged.moddev")
}

val javaRelease = property("java_version").toString().toInt()
java {
    toolchain { languageVersion = JavaLanguageVersion.of(javaRelease) }
}

tasks.withType<JavaCompile>().configureEach { options.release = javaRelease }

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    mavenCentral()
}

val client: SourceSet = sourceSets.create("client") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

neoForge {
    neoFormVersion = property("neo_form_version").toString()
    accessTransformers.from(rootProject.file("common/src/main/resources/META-INF/accesstransformer.cfg"))
    addModdingDependenciesTo(client)
}

// Fabric and NeoForge both ship Mixin and MixinExtras at runtime, so common only compiles against them (as the template).
val mixin = "net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7"
val mixinExtras = "io.github.llamalad7:mixinextras-common:0.5.3"

dependencies {
    api(project(":core"))
    "clientImplementation"(project(":core"))
    "clientCompileOnly"(mixin)
    "clientCompileOnly"(mixinExtras)
}

val checkCommonImports = tasks.register("checkCommonImports") {
    group = "verification"
    description = "Fails when common imports a mod loader"
    val sources = fileTree(rootProject.file("common/src")) { include("**/*.java") }
    inputs.files(sources)
    doLast {
        val banned = Regex("""^\s*import\s+(static\s+)?(net\.fabricmc|net\.neoforged|net\.minecraftforge)\..*""", RegexOption.MULTILINE)
        val hits = sources.files.flatMap { f -> banned.findAll(f.readText()).map { "${f.relativeTo(rootDir)}: ${it.value.trim()}" } }
        if (hits.isNotEmpty()) throw GradleException("common must stay free of mod loaders:\n" + hits.joinToString("\n"))
    }
}
tasks.named("check") { dependsOn(checkCommonImports) }
// `build` compiles both source sets, so a loader-only API used in common fails here even before :fabric builds.
tasks.named("assemble") { dependsOn(tasks.named("compileClientJava")) }
// MDG reads the resources (access transformer) when it sets up Minecraft; on a non-active version they are
// Stonecutter's generated copy.
tasks.named("createMinecraftArtifacts") { dependsOn("stonecutterGenerate") }
