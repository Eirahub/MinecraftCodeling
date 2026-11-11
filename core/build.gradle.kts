// Pure Java shared by every Minecraft version and loader (issue #185): protocol, sessions, robot commands, block
// and item ids, the relay. No Minecraft or loader on the classpath; checkPureImports also fails on such an import.
plugins {
    `java-library`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

// Main code runs on every Minecraft version, the oldest planned one (1.20.1) on Java 17 (issue #188). Tests stay on 25.
tasks.withType<JavaCompile>().configureEach { options.release = 25 }
tasks.named<JavaCompile>("compileJava") { options.release = 17 }

repositories { mavenCentral() }

// Gson and SLF4J come with Minecraft at runtime, so they are compileOnly here. Keep the versions equal to the ones
// Minecraft 26.3 ships (./gradlew :fabric:dependencies --configuration compileClasspath). The relay needs no Netty.
val gson = "com.google.code.gson:gson:2.14.0"
val slf4j = "org.slf4j:slf4j-api:2.0.17"

dependencies {
    compileOnly(gson)
    compileOnly(slf4j)
    testImplementation(gson)
    testImplementation(slf4j)
    // RelayHardeningTest captures the log to prove the token is never in it.
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // BlockEnumTest and ItemEnumTest read (and regenerate) these editor files.
    inputs.files("../target/libs/core/blocks.d.ts", "../target/libs/core/items.d.ts", "../target/locales/nb/codeling/core-strings.json")
}

val checkPureImports = tasks.register("checkPureImports") {
    group = "verification"
    description = "Fails when core imports Minecraft, Mojang or a mod loader"
    val sources = fileTree("src") { include("**/*.java") }
    inputs.files(sources)
    doLast {
        val banned = Regex("""^\s*import\s+(static\s+)?(net\.minecraft|com\.mojang|net\.fabricmc|net\.neoforged|net\.minecraftforge)\..*""", RegexOption.MULTILINE)
        val hits = sources.files.flatMap { f -> banned.findAll(f.readText()).map { "${f.relativeTo(projectDir)}: ${it.value.trim()}" } }
        if (hits.isNotEmpty()) throw GradleException("core must stay free of Minecraft and loaders:\n" + hits.joinToString("\n"))
    }
}
tasks.named("check") { dependsOn(checkPureImports) }

// Issue #188: the relay on the Java and library versions of older Minecraft, with no Netty on the classpath at all.
// RelayCompatCheck (src/compat, Java 17) starts the relay, loads the packaged editor and does a WebSocket round trip.
// Java 17 with Gson 2.10 and SLF4J 2.0.1 is what Minecraft 1.20.1 ships, Java 21 with 2.10.1 and 2.0.9 is 1.21.1
// (Mojang's version manifests). Needs the built editor in ../target/built/packaged (npm run staticpkg).
val compat = sourceSets.create("compat") { compileClasspath += sourceSets.main.get().output }
tasks.named<JavaCompile>("compileCompatJava") { options.release = 17 }
val compatEditor = tasks.register<Sync>("compatEditor") {
    // The entry page and the top-level scripts and styles are enough to prove serving; the full editor is 50 MB.
    from(rootProject.layout.projectDirectory.dir("target/built/packaged")) { include("index.html", "*.js", "*.css") }
    into(layout.buildDirectory.dir("compat-editor/codeling-editor"))
    doLast { if (!destinationDir.resolve("index.html").exists()) throw GradleException("Build the editor first: cd target && npm run staticpkg") }
}
val relayCompatTest = tasks.register("relayCompatTest") {
    group = "verification"
    description = "Runs the relay on Java 17 and 21 with the Gson and SLF4J of Minecraft 1.20.1 and 1.21.1, no Netty"
}
// Java version to Gson, SLF4J and a free port (the JUnit relay tests use 25595 to 25599, the editor suite 25591).
for ((java, libs) in mapOf(17 to listOf("2.10", "2.0.1", "25593"), 21 to listOf("2.10.1", "2.0.9", "25594"))) {
    val runtime = configurations.create("compatJava$java")
    dependencies {
        runtime("com.google.code.gson:gson:${libs[0]}")
        runtime("org.slf4j:slf4j-api:${libs[1]}")
    }
    val run = tasks.register<JavaExec>("relayCompatJava$java") {
        group = "verification"
        description = "The relay on Java $java, Gson ${libs[0]}, SLF4J ${libs[1]}, no Netty"
        dependsOn(compatEditor)
        classpath = files(sourceSets.main.get().output, compat.output, layout.buildDirectory.dir("compat-editor"), runtime)
        mainClass = "codeling.relay.RelayCompatCheck"
        args(libs[2])
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(java) }
    }
    relayCompatTest { dependsOn(run) }
}
