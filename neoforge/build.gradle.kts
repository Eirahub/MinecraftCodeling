// NeoForge loader module (issue #187), after the neoforge module of jaredlll08's MultiLoader-Template (branch 26.3):
// ModDevGradle with the NeoForge version, common's access transformer, and core and common compiled into this jar.
// Stonecutter (issue #189) runs this script once per Minecraft version: :neoforge:26.3 and :neoforge:1.21.1, each in
// versions/<version>/ with its own gradle.properties (NeoForge, Java, metadata range). MDG handles both: 26.x is
// unobfuscated, 1.21.1 runs on Mojang's names in development as well.
plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev")
}

val javaRelease = property("java_version").toString().toInt()
// Game rules before 1.21.11's rewrite need a few members opened (NeoForgePlatform); this AT ships on 1.21.1 only.
val oldGameRules = stonecutter.current.parsed < "1.21.11"
val oldRulesAt = "META-INF/accesstransformer-1.21.1.cfg"
// Vanilla's GameTest rewrite (test registries, --tests and --report) came with 1.21.5.
val oldGameTests = stonecutter.current.parsed < "1.21.5"
// codeling.mixins.json: Mixin's compatibility level follows the Java version, and before 26.3 one mixin more
// (GameRendererMixin hides the hand while the robot camera is on; 26.3 does that from the level render state).
val mixinProps = mapOf(
    "mixin_java" to "JAVA_$javaRelease",
    "extra_client_mixins" to if (stonecutter.current.parsed < "26.3") ", \"GameRendererMixin\"" else "",
)
java {
    toolchain { languageVersion = JavaLanguageVersion.of(javaRelease) }
}

tasks.withType<JavaCompile>().configureEach { options.release = javaRelease }

// Version, group, jar name rule (checkJarNames) and the editor in the resources, shared with :fabric.
apply(from = rootProject.file("gradle/loader.gradle.kts"))

// Server GameTests for NeoForge (smoke tests; the full suite runs on Fabric). Part of the codeling mod in dev runs,
// never in the jar.
val gametest: SourceSet = sourceSets.create("gametest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

neoForge {
    version = project.property("neoforge_version").toString()
    accessTransformers.from(rootProject.file("common/src/main/resources/META-INF/accesstransformer.cfg"))
    if (oldGameRules) accessTransformers.from(rootProject.file("neoforge/src/main/resources/$oldRulesAt"))
    addModdingDependenciesTo(gametest)
    mods {
        register("codeling") {
            sourceSet(sourceSets.main.get())
            sourceSet(gametest)
        }
    }
    runs {
        configureEach { ideName = "NeoForge ${name.replaceFirstChar { it.uppercase() }} (${project.path})" }
        register("client") {
            client()
            gameDirectory = file("run/client")
        }
        register("server") {
            server()
            gameDirectory = file("run/server")
            programArgument("--nogui")
        }
        // ./gradlew :neoforge:<version>:runGameTestServer: a headless server that runs every codeling GameTest and exits with the
        // number of failed required tests, so `check` (and `build`) fails on a red test.
        register("gameTestServer") {
            type = "gameTestServer"
            sourceSet = gametest
            gameDirectory = file("run/gametest")
            val report = layout.buildDirectory.file("test-results/gameTest/TEST-gametest.xml").get().asFile.path
            // 1.21.1's GameTest server takes no test filter or report argument: NeoForge runs the codeling namespace and
            // NeoForgeSmokeGameTests writes the report from this property.
            if (oldGameTests) systemProperties.putAll(mapOf("neoforge.enabledGameTestNamespaces" to "codeling", "codeling.gameTestReport" to report))
            else programArguments.addAll("--tests", "codeling:*", "--report", report)
        }
        // ./gradlew :neoforge:<version>:runClientSmoke: starts the NeoForge client, checks that the mixins applied and the relay
        // serves the editor, then quits (NeoForgeClientSmoke). It opens a game window, so it is not part of `build`.
        register("clientSmoke") {
            client()
            sourceSet = gametest
            gameDirectory = file("run/clientSmoke")
            systemProperty("codeling.clientSmoke", "true")
        }
    }
}

// core (pure Java) and common (vanilla Minecraft) are compiled into this module, as MultiLoader-Template does with
// common: so their classes and the editor files share the mod's class loader (RelayServer loads codeling-editor/
// through its own class loader). NeoForge has no split client source set; the client entrypoint is @Mod(dist = CLIENT).
// common's sources for this Minecraft version: src/ itself for the active version, else Stonecutter's preprocessed
// copy from :common:<version> (built by its stonecutterGenerate tasks).
val coreDir = rootProject.layout.projectDirectory.dir("core/src/main/java")
val commonNode = project(":common:${stonecutter.current.project}")
fun commonSrc(set: String, kind: String): ConfigurableFileCollection =
    if (stonecutter.current.isActive) files(rootProject.file("common/src/$set/$kind"))
    else files(commonNode.layout.buildDirectory.dir("generated/stonecutter/$set/$kind"))
        .builtBy("${commonNode.path}:stonecutterGenerate${if (set == "main") "" else set.replaceFirstChar { it.uppercase() }}")
tasks.named<JavaCompile>("compileJava") {
    source(coreDir, commonSrc("main", "java"), commonSrc("client", "java"))
}
tasks.named<ProcessResources>("processResources") {
    from(commonSrc("main", "resources"), commonSrc("client", "resources"))
    val props = mapOf(
        "version" to project.version,
        // A target that covers several Minecraft versions (26.1 to 26.1.2) accepts each one's NeoForge line through
        // neoforge_range; otherwise the NeoForge it was built against or newer.
        "neoforge_range" to (project.findProperty("neoforge_range")?.toString()
            ?: "[${project.property("neoforge_version")},)"),
        "minecraft_range" to project.property("minecraft_range").toString(),
        // GameRuleType, which enumextensions.json extends, came with 1.21.11's game rule rewrite; before it, the
        // second access transformer stands in.
        "enum_extensions" to if (oldGameRules) "" else "enumExtensions = \"META-INF/enumextensions.json\"",
        "extra_access_transformers" to if (oldGameRules) "[[accessTransformers]]\nfile = \"$oldRulesAt\"" else "",
    )
    if (oldGameRules) exclude("META-INF/enumextensions.json") else exclude(oldRulesAt)
    inputs.properties(props)
    inputs.properties(mixinProps)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    filesMatching("codeling.mixins.json") { expand(mixinProps) }
}

// The JUnit-like report (--report above) needs its directory; CI uploads it.
tasks.named("runGameTestServer") {
    val reports = layout.buildDirectory.dir("test-results/gameTest")
    doFirst { reports.get().asFile.mkdirs() }
}
tasks.named("check") { dependsOn("runGameTestServer") }
// MDG reads the resources (access transformer) when it sets up Minecraft; on a non-active version they are
// Stonecutter's generated copy.
tasks.named("createMinecraftArtifacts") { dependsOn("stonecutterGenerate") }
