// Build logic every loader module (fabric, neoforge) shares: the jar name rule and the editor in the jar. Each loader's
// build.gradle.kts applies it with apply(from = ...) after its loader plugin, so the loader's tasks exist here.

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

// Jar name rule (README, docs/architecture.md): <modid>-<loader>-<minecraftversion>-<modversion>.jar, e.g.
// codeling-fabric-26.3-0.0.1.jar. Stonecutter (issue #189) makes one project per loader and version, :fabric:26.3:
// the loader part is the module's name (the parent project), the Minecraft version is the project's own name.
extensions.configure<BasePluginExtension> {
    archivesName = "${providers.gradleProperty("mod_id").get()}-${project.parent!!.name}-${project.name}"
}

// Fails when a jar in build/libs breaks the jar name rule (see archivesName above).
val checkJarNames = tasks.register("checkJarNames") {
    group = "verification"
    description = "Fails when a jar in build/libs does not follow <modid>-<loader>-<minecraftversion>-<modversion>.jar"
    val libs = layout.buildDirectory.dir("libs")
    dependsOn(tasks.withType<Jar>().matching { it.destinationDirectory.get().asFile == libs.get().asFile })
    doLast {
        val rule = Regex("""^codeling-(fabric|neoforge|forge)-\d+(\.\d+)+-\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?(-sources)?\.jar$""")
        val bad = libs.get().asFile.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".jar") && !rule.matches(it) }
        if (bad.isNotEmpty()) throw GradleException("Jar names break the rule <modid>-<loader>-<minecraftversion>-<modversion>.jar: $bad")
    }
}
tasks.named("build") { dependsOn(checkJarNames) }

// The static editor built by `npm run staticpkg` in ../target goes into the jar under codeling-editor/. Loom's split
// environment has client resources of their own; NeoForge has one resource set.
val editorDir = rootProject.layout.projectDirectory.dir("target/built/packaged")
val editorResources = if ("processClientResources" in tasks.names) "processClientResources" else "processResources"
tasks.named<ProcessResources>(editorResources) {
    from(editorDir) {
        into("codeling-editor")
        // pxt sub-webapps and the unused embed runner (spike item 6). The telemetry snippet is
        // removed by target/docfiles/apptracking.html, so ai.2.min.js is unused too.
        exclude(
            "kiosk/**", "kiosk.html", "teachertool/**", "teachertool.html",
            "multiplayer/**", "multiplayer.html", "skillmap/**", "skillmap.html",
            "tutorialtool/**", "tutorialtool.html", "authcode/**", "authcode.html",
            "react-common-*.css", "rtlreact-common-*.css",
            // docs.html needs embed.js + pxtembed.js for the side docs panel, so only runnerembed.js goes.
            "runnerembed.js", "ai.2.min.js",
        )
    }
    // pxt hardcodes "Microsoft MakeCode" in the editor UI (issue #16). Patch the copied main.js and fail the
    // build when a pattern is gone, so a pxt upgrade cannot bring the branding back unnoticed.
    val mainJs = destinationDir.resolve("codeling-editor/main.js")
    doLast {
        val patches = listOf(
            // About box: one line with the link instead of "Microsoft MakeCode version: 13.3.1".
            "E(\"Microsoft MakeCode\",o.pxt,`https://github.com/Microsoft/pxt/releases/tag/v\${o.pxt}`)" to
                "(0,i.jsx)(\"p\",{children:(0,i.jsx)(\"a\",{href:`https://github.com/Microsoft/pxt/releases/tag/v\${o.pxt}`,target:\"_blank\",rel:\"noopener noreferrer\",children:`pxt editor \${o.pxt} (MIT, Microsoft)`})})",
            "lf(\"MakeCode {0} Logo, return to home page\",e.boardName)" to "lf(\"Codeling, return to home page\")",
            "different version of Microsoft MakeCode." to "different version of the Codeling editor.",
            // Screen-reader heading on the home screen.
            "lf(\"MakeCode Home\")" to "lf(\"Codeling Home\")",
        )
        var text = mainJs.readText()
        for ((from, to) in patches) {
            if (!text.contains(from)) throw GradleException("Editor branding patch: pattern not found in $mainJs (pxt upgrade?): $from")
            text = text.replace(from, to)
        }
        mainJs.writeText(text)
    }
}
