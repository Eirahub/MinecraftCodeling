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
// version the left side is right. Every version group from 1.21.1 to 26.3 is built, so a boundary sits between two
// built versions; a change inside a group is named by the group's first version (see docs/architecture.md).
stonecutter parameters {
    // Minecraft 1.21.11 renamed ResourceLocation to Identifier; the methods we use are the same.
    // ResourceKey.location() became identifier() with it.
    replacements.string(current.parsed < "1.21.11") {
        replace("Identifier", "ResourceLocation")
        replace(".identifier()", ".location()")
    }
    replacements.string(current.parsed < "1.21.2") { replace(".getMinY()", ".getMinBuildHeight()") }
    replacements.string(current.parsed < "1.21.5") { replace(".snapTo(", ".moveTo(") }
    // GameProfile became a record with authlib 7.
    replacements.string(current.parsed < "1.21.9") { replace("getGameProfile().name()", "getGameProfile().getName()") }
    replacements.string(current.parsed < "26.1") {
        replace("GuiGraphicsExtractor", "GuiGraphics")
        replace("ContainerInput", "ClickType")
        replace("renderer.state.level.CameraRenderState", "renderer.state.CameraRenderState")
        // ItemStackSlotDisplay holds an ItemStack before 26.1 (an ItemStackTemplate from 26.1).
        replace("s.stack().item().value()", "s.stack().getItem()")
    }
    // EntityTypes came with 26.2. Only these two: replacements run backwards where the condition is false, and from
    // 26.2 on both EntityTypes and EntityType exist. RobotGameTests guards its import.
    replacements.string(current.parsed < "26.2") {
        replace("EntityTypes.PIG", "EntityType.PIG")
        replace("EntityTypes.ZOMBIE", "EntityType.ZOMBIE")
    }
    // 1.21.5 to 1.21.10 take an assert's message only as a Component (see GameTestAsserts in both gametest folders).
    replacements.string(current.parsed >= "1.21.5" && current.parsed < "1.21.11") {
        replace("helper.assertTrue(", "GameTestAsserts.assertTrue(helper, ")
        replace("helper.assertFalse(", "GameTestAsserts.assertFalse(helper, ")
        replace("helper.assertValueEqual(", "GameTestAsserts.assertValueEqual(helper, ")
    }
    // fail takes a String from 1.21.9 (checked on 1.21.10).
    replacements.string(current.parsed >= "1.21.5" && current.parsed < "1.21.9") {
        replace("helper.fail(", "GameTestAsserts.fail(helper, ")
    }
    // Renames that came with 26.3.
    replacements.string(current.parsed < "26.3") { replace("PushReaction.IGNORE_ENTITY", "PushReaction.IGNORE") }
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
