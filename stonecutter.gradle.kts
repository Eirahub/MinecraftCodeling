// Root build script and Stonecutter controller (issue #189). The plugins are declared here so every versioned project
// (:common:<v> with ModDevGradle, :fabric:<v> with Loom, :neoforge:<v>) shares one plugin classloader, as in jaredlll08's
// MultiLoader-Template. Loom comes in two flavours: fabric-loom for unobfuscated 26.x, fabric-loom-remap for 1.21.1 and
// older; loom-back-compat applies the right one per version (fabric/build.gradle.kts).
plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.19.4" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.19.4" apply false
    id("net.neoforged.moddev") version "2.0.148" apply false
}

// The version whose code src/ holds right now. Keep 26.3 in git (settings.gradle.kts, vcsVersion); the other versions
// build from preprocessed copies in <module>/versions/<version>/build/generated/stonecutter/.
stonecutter active "26.3"

// The pxt target in target/ is an npm project; this task wraps it so it can run
// from Gradle (IntelliJ Community has no npm run configurations).
val npm = if (System.getProperty("os.name").lowercase().contains("win")) "npm.cmd" else "npm"

tasks.register<Exec>("buildEditor") {
    group = "codeling"
    description = "npm ci + pxt buildtarget + pxt staticpkg in target/ (output: target/built/packaged)"
    workingDir = file("target")
    commandLine("cmd", "/c", "$npm ci && $npm run build && $npm run staticpkg")
    if (!System.getProperty("os.name").lowercase().contains("win")) {
        commandLine("sh", "-c", "$npm ci && $npm run build && $npm run staticpkg")
    }
}

// Version differences that are pure renames, applied to every versioned project's sources. Everything else is a
// Stonecutter condition in the code (//? if >=1.21.6 ...), see docs/architecture.md "Several Minecraft versions".
// The left side is the 26.x name in src/, the right side what Minecraft 1.21.1 calls it. A condition says from which
// version the left side is right: 1.21.11 where the change is documented, else 26.1 (only 26.3 and 1.21.1 are built,
// so any boundary between them gives the same result).
stonecutter parameters {
    // Minecraft 1.21.11 renamed ResourceLocation to Identifier; the methods we use are the same.
    replacements.string(current.parsed < "1.21.11") { replace("Identifier", "ResourceLocation") }
    replacements.string(current.parsed < "26.1") {
        replace(".snapTo(", ".moveTo(")
        replace("getGameProfile().name()", "getGameProfile().getName()")
        replace(".getMinY()", ".getMinBuildHeight()")
        replace("GuiGraphicsExtractor", "GuiGraphics")
        replace("ContainerInput", "ClickType")
    }
    // Renames that came with 26.3.
    replacements.string(current.parsed < "26.3") {
        replace("PushReaction.IGNORE_ENTITY", "PushReaction.IGNORE")
        replace("EntityTypes", "EntityType")
    }
    // Fabric's own @GameTest (structure, maxTicks, environment) came with 1.21.5. Before that the tests use vanilla's
    // annotation with Fabric's empty structure, and a test environment becomes a batch whose @BeforeBatch sets the rule.
    // A bare @GameTest gets Fabric's default of 20 ticks.
    replacements.string(current.parsed < "1.21.5") {
        replace("import net.fabricmc.fabric.api.gametest.v1.GameTest;", "import net.minecraft.gametest.framework.GameTest;")
        replace("@GameTest(maxTicks = ", "@GameTest(template = \"fabric-gametest-api-v1:empty\", timeoutTicks = ")
        replace("@GameTest\n", "@GameTest(template = \"fabric-gametest-api-v1:empty\", timeoutTicks = 20)\n")
        replace(", environment = ", ", batch = ")
    }
}
