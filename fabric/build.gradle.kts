// Fabric loader module. Stonecutter (issue #189) runs this script once per Minecraft version: :fabric:26.3 and
// :fabric:1.21.1, each in versions/<version>/ with its own gradle.properties (Fabric API, Java, metadata range).
// loom-back-compat applies net.fabricmc.fabric-loom on 26.x (unobfuscated, no mappings, plain `jar`) and
// net.fabricmc.fabric-loom-remap on 1.21.1 (Mojang's mappings, the mod jar is `remapJar`). modImplementation is an
// alias of implementation on 26.x, so one dependency block serves both.
plugins {
    id("dev.kikugie.stonecutter")
    id("dev.kikugie.loom-back-compat")
}

val mc = stonecutter.current.version
// The version whose full test set runs (client GameTests, editor suite, standalone relay); see README "Build".
val isMain = mc == "26.3"
val javaRelease = property("java_version").toString().toInt()
// codeling.mixins.json: Mixin's compatibility level follows the Java version, and before 26.3 one mixin more
// (GameRendererMixin hides the hand while the robot camera is on; 26.3 does that from the level render state).
val mixinProps = mapOf(
    "mixin_java" to "JAVA_$javaRelease",
    "extra_client_mixins" to if (stonecutter.current.parsed < "26.3") ", \"GameRendererMixin\"" else "",
)

java {
    toolchain { languageVersion = JavaLanguageVersion.of(javaRelease) }
}

loom {
    splitEnvironmentSourceSets()
    // Loom only generates IntelliJ run configs for the root project by default; :fabric:<version> is a subproject.
    runs { configureEach { generateRunConfig = true } }
    mods {
        register("codeling") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}
// Version, group, jar name rule (checkJarNames) and the editor in the client resources, shared with :neoforge.
apply(from = rootProject.file("gradle/loader.gradle.kts"))

// Server GameTests live in src/gametest (own mod id, not in the jar). Loom adds runGameTest and makes `check` (so
// `build`) depend on it; it starts a headless GameTest server and fails the build on a failed test.
// Client GameTests (same source set, entrypoint fabric-client-gametest) start a real game window (2 to 3 GB), so they
// only run with ./gradlew :fabric:26.3:runClientGameTest, never from `build`. Fabric API for 1.21.1 has no client
// GameTest API (it came with 1.21.5); there runClientSmoke starts the client instead (below).
// Screenshot templates: src/gametest/resources/templates.
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "codeling-gametest"
        enableClientGameTests = isMain
    }
}
loom.runs.named("gameTest") {
    systemProperties.put("fabric-api.gametest.report-file", layout.buildDirectory.file("test-results/gameTest/TEST-gametest.xml").get().asFile.path)
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version").toString()}")
    implementation(project(":core"))

    testImplementation(sourceSets["client"].output)
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// common (issue #186) is compiled into this module, as MultiLoader-Template does: its main sources with main, its
// client sources with client, and its resources (assets, mixin config) copied in. So the jar holds core, common and
// fabric classes, and Loom checks common's client code against the split environment like the fabric code.
// common's sources for this Minecraft version: src/ itself for the active version, else Stonecutter's preprocessed
// copy from :common:<version> (built by its stonecutterGenerate tasks).
val commonNode = project(":common:${stonecutter.current.project}")
fun commonSrc(set: String, kind: String): ConfigurableFileCollection =
    if (stonecutter.current.isActive) files(rootProject.file("common/src/$set/$kind"))
    else files(commonNode.layout.buildDirectory.dir("generated/stonecutter/$set/$kind"))
        .builtBy("${commonNode.path}:stonecutterGenerate${if (set == "main") "" else set.replaceFirstChar { it.uppercase() }}")
tasks.named<JavaCompile>("compileJava") { source(commonSrc("main", "java")) }
tasks.named<JavaCompile>("compileClientJava") { source(commonSrc("client", "java")) }

tasks.named<ProcessResources>("processClientResources") {
    from(commonSrc("client", "resources"))
    inputs.properties(mixinProps)
    filesMatching("codeling.mixins.json") { expand(mixinProps) }
}

tasks.named<ProcessResources>("processResources") {
    from(commonSrc("main", "resources"))
    val props = mapOf(
        "version" to project.version,
        "minecraft_dependency" to project.property("minecraft_dependency").toString(),
        "java_version" to javaRelease,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.withType<JavaCompile>().configureEach { options.release = javaRelease }

// core (issue #185) has no fabric.mod.json, so its classes go straight into the mod jar instead of a nested jar.
val coreJar = project(":core").tasks.named<Jar>("jar")
tasks.jar {
    dependsOn(coreJar)
    from(coreJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/**") }
}

configurations.named("testImplementation") {
    extendsFrom(configurations["clientImplementation"])
}
configurations.named("testRuntimeOnly") {
    extendsFrom(configurations["clientRuntimeClasspath"])
}

tasks.test {
    useJUnitPlatform()
}

if (isMain) {
    // The end-to-end client GameTest (issue #135) drives the editor with e2e/editor-run.mjs (Node, pinned playwright-core).
    val e2eDir = rootProject.layout.projectDirectory.dir("e2e")
    val installE2e = tasks.register<Exec>("installE2e") {
        group = "codeling"
        description = "npm ci in e2e/ for the end-to-end client GameTest"
        workingDir = e2eDir.asFile
        commandLine(if (System.getProperty("os.name").lowercase().contains("win")) listOf("cmd", "/c", "npm ci --no-audit --no-fund") else listOf("sh", "-c", "npm ci --no-audit --no-fund"))
        inputs.files(e2eDir.file("package.json"), e2eDir.file("package-lock.json"))
        outputs.dir(e2eDir.dir("node_modules"))
    }
    loom.runs.named("clientGameTest") {
        property("codeling.e2eDir", e2eDir.asFile.absolutePath)
    }
    tasks.named("runClientGameTest") { dependsOn(installE2e) }
    // Editor test suite (issue #136): e2e/editor-suite.mjs drives the editor in headless Chromium against the standalone
    // relay, which the script starts itself from this classpath (so the token never leaves the Node process). Not part
    // of `build`; build the target first (target/built/packaged), as for the jar. The editor is the same for every
    // Minecraft version, so the suite runs once, here.
    tasks.register<Exec>("editorTest") {
        group = "codeling"
        description = "Editor suite: round trip of every block, example programs, old project fixtures, branding (headless Chromium)"
        val relayClasspath = sourceSets["client"].runtimeClasspath
        dependsOn(installE2e, relayClasspath)
        workingDir = e2eDir.asFile
        commandLine("node", "editor-suite.mjs")
        val java = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(javaRelease) }
        doFirst {
            environment("CODELING_JAVA", java.get().executablePath.asFile.absolutePath)
            environment("CODELING_RELAY_CLASSPATH", relayClasspath.asPath)
        }
    }

    // Runs the relay and editor server without Minecraft: ./gradlew :fabric:26.3:runRelay --args="--print-url"
    tasks.register<JavaExec>("runRelay") {
        group = "codeling"
        description = "Start the editor + relay server standalone (no Minecraft)"
        classpath = sourceSets["client"].runtimeClasspath
        mainClass = "codeling.relay.StandaloneRelay"
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(javaRelease) }
    }
} else {
    // The client GameTests need fabric-client-gametest-api-v1 (1.21.5 and later); they only run on the main version.
    tasks.named<JavaCompile>("compileGametestJava") {
        exclude("**/RobotClientGameTest.java", "**/EditorEndToEndClientGameTest.java")
    }
    // No client GameTest API in Fabric API for this version: ./gradlew :fabric:1.21.1:runClientSmoke starts the client
    // once (a game window, not part of `build`), checks that the camera mixins were merged and the relay serves the
    // editor, logs "Codeling client smoke: OK" and quits (codeling.gametest.FabricClientSmoke). Same idea as
    // :neoforge:<version>:runClientSmoke.
    loom.runs.register("clientSmoke") {
        client()
        source(sourceSets["gametest"])
        runDir("build/run/clientSmoke")
        property("codeling.clientSmoke", "true")
        ideConfigGenerated(false)
    }
}
