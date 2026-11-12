<#
.SYNOPSIS
    Sets up one MultiMC instance per loader and Minecraft version that Codeling supports, in a "Codeling" group,
    with the right Java and mods, for testing every jar.

.DESCRIPTION
    Run it from a MultiMC folder (the one with MultiMC.exe), or pass -MultiMC. For every row in $Targets below it
    makes an instance "Codeling <Loader> <Minecraft version>" with:
      - Minecraft and the loader (Fabric Loader from gradle.properties, the newest NeoForge for that version),
      - Java 21 for 1.21.x and Java 25 for 26.x (found under Program Files, or given with -Java21 / -Java25),
      - the Codeling jar of the target that covers that version, and Fabric API on Fabric.
    Running it again updates the instances in place: it replaces the Codeling and Fabric API jars, the loader and
    the Java path, and keeps worlds, options and anything else in the instance. Close MultiMC first: it rewrites
    instgroups.json on exit.

    Jars come from the repo build output (<loader>/versions/<target>/build/libs) when the script runs from a
    checkout, from -JarDir (a folder with the jars, for example a downloaded release), or from a GitHub release
    with -Release. A target without a jar still gets its instances, with a warning, so a later run can fill them.

.EXAMPLE
    cd C:\MultiMC; & C:\path\to\MinecraftCodeling\scripts\setup-multimc.ps1
.EXAMPLE
    .\setup-multimc.ps1 -MultiMC C:\MultiMC -Release v0.0.1 -Only "*1.21.1*"
#>
param(
    # The MultiMC folder. Default: the current folder.
    [string]$MultiMC = (Get-Location).Path,
    # A folder with codeling-<loader>-<target>-<version>.jar files, instead of the repo build output.
    [string]$JarDir,
    # A GitHub release tag (for example v0.0.1) to download the jars from, instead of the repo build output.
    [string]$Release,
    # javaw.exe for Java 21 and 25, when the script cannot find them.
    [string]$Java21,
    [string]$Java25,
    # Only instances whose name matches this wildcard, for example "*Fabric*" or "*26.*".
    [string]$Only = "*",
    # The instance group in MultiMC.
    [string]$Group = "Codeling"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # Invoke-WebRequest is very slow with the progress bar in PowerShell 5.1
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$RepoRoot = Split-Path -Parent $PSScriptRoot
$GitHubRepo = "Eirahub/MinecraftCodeling"

# One row per Stonecutter target (settings.gradle.kts): the jar's Minecraft version, then every Minecraft version
# that jar supports (its fabric.mod.json / neoforge.mods.toml range). Keep in step with settings.gradle.kts and
# <loader>/versions/<target>/gradle.properties. NeoForge has no build for 1.21 itself, so its list starts at 1.21.1.
$Targets = @(
    @{ Target = "1.21.1";  Fabric = @("1.21", "1.21.1");            NeoForge = @("1.21.1") },
    @{ Target = "1.21.3";  Fabric = @("1.21.2", "1.21.3");          NeoForge = @("1.21.2", "1.21.3") },
    @{ Target = "1.21.4";  Fabric = @("1.21.4");                    NeoForge = @("1.21.4") },
    @{ Target = "1.21.5";  Fabric = @("1.21.5");                    NeoForge = @("1.21.5") },
    @{ Target = "1.21.8";  Fabric = @("1.21.6", "1.21.7", "1.21.8"); NeoForge = @("1.21.6", "1.21.7", "1.21.8") },
    @{ Target = "1.21.10"; Fabric = @("1.21.9", "1.21.10");         NeoForge = @("1.21.9", "1.21.10") },
    @{ Target = "1.21.11"; Fabric = @("1.21.11");                   NeoForge = @("1.21.11") },
    @{ Target = "26.1.2";  Fabric = @("26.1", "26.1.1", "26.1.2");  NeoForge = @("26.1", "26.1.1", "26.1.2") },
    @{ Target = "26.2";    Fabric = @("26.2");                      NeoForge = @("26.2") },
    @{ Target = "26.3";    Fabric = @("26.3");                      NeoForge = @("26.3") }
)

function Write-Utf8([string]$Path, [string]$Text) {
    # No byte order mark: PowerShell 5.1's -Encoding UTF8 writes one.
    [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding($false)))
}

function Get-Text([string]$Url) {
    for ($try = 1; $try -le 3; $try++) {
        try { return (Invoke-WebRequest -UseBasicParsing -Uri $Url).Content }
        catch { if ($try -eq 3) { throw "Could not download ${Url}: $($_.Exception.Message)" }; Start-Sleep -Seconds 2 }
    }
}

function Save-File([string]$Url, [string]$Path) {
    for ($try = 1; $try -le 3; $try++) {
        try { Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Path; return }
        catch { if ($try -eq 3) { throw "Could not download ${Url}: $($_.Exception.Message)" }; Start-Sleep -Seconds 2 }
    }
}

function Get-MavenVersions([string]$MetadataUrl) {
    $xml = [xml](Get-Text $MetadataUrl)
    return @($xml.metadata.versioning.versions.version)
}

# Sorts "0.141.6+1.21.11" or "21.8.57-beta" by their numeric parts.
function Get-VersionKey([string]$Version) {
    $nums = [regex]::Matches(($Version -split '[+-]')[0], '\d+') | ForEach-Object { '{0:D8}' -f [int]$_.Value }
    return ($nums -join '.')
}

# Java: the newest javaw.exe of a major version under the usual install folders.
function Find-Java([int]$Major) {
    $roots = @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft",
               "$env:ProgramFiles\Zulu", "$env:ProgramFiles\BellSoft", "$env:ProgramFiles\Amazon Corretto",
               "$env:ProgramFiles\Semeru")
    $found = foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        foreach ($dir in Get-ChildItem $root -Directory -ErrorAction SilentlyContinue) {
            $release = Join-Path $dir.FullName "release"
            $javaw = Join-Path $dir.FullName "bin\javaw.exe"
            if (-not ((Test-Path $release) -and (Test-Path $javaw))) { continue }
            $line = Select-String -Path $release -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
            if (-not $line) { continue }
            $ver = $line.Matches[0].Groups[1].Value
            if ([int](($ver -split '\.')[0]) -eq $Major) {
                [pscustomobject]@{ Path = $javaw; Version = $ver }
            }
        }
    }
    $best = $found | Sort-Object { Get-VersionKey $_.Version } | Select-Object -Last 1
    if ($best) { return $best.Path }
    return $null
}

# ---------------------------------------------------------------------------------------------------------------
# Checks and inputs

$MultiMC = (Resolve-Path $MultiMC).Path
$instancesDir = Join-Path $MultiMC "instances"
if (-not ((Test-Path (Join-Path $MultiMC "MultiMC.exe")) -or (Test-Path $instancesDir))) {
    throw "$MultiMC does not look like a MultiMC folder (no MultiMC.exe or instances folder). Run the script there or pass -MultiMC."
}
if (Get-Process -Name MultiMC -ErrorAction SilentlyContinue) {
    Write-Warning "MultiMC is running. Close it before going on, or it may overwrite the group list when it exits."
}
New-Item -ItemType Directory -Force $instancesDir | Out-Null

$java = @{ 21 = $Java21; 25 = $Java25 }
foreach ($major in 21, 25) {
    if (-not $java[$major]) { $java[$major] = Find-Java $major }
    if ($java[$major]) { Write-Host "Java ${major}: $($java[$major])" }
    else { Write-Warning "No Java $major found; pass -Java$major <path to javaw.exe>. Those instances use MultiMC's default Java." }
}

# Shared versions from the repo when there is one, else the values of this release.
$loaderVersion = "0.20.1"
$modVersion = $null
$props = Join-Path $RepoRoot "gradle.properties"
if (Test-Path $props) {
    foreach ($line in Get-Content $props) {
        if ($line -match '^loader_version=(.+)$') { $loaderVersion = $Matches[1].Trim() }
        if ($line -match '^mod_version=(.+)$') { $modVersion = $Matches[1].Trim() }
    }
}
Write-Host "Fabric Loader $loaderVersion"

# Jars from a release: download every asset into a cache folder and use it as -JarDir.
if ($Release) {
    $JarDir = Join-Path $MultiMC "codeling-jars\$Release"
    New-Item -ItemType Directory -Force $JarDir | Out-Null
    $info = Get-Text "https://api.github.com/repos/$GitHubRepo/releases/tags/$Release" | ConvertFrom-Json
    foreach ($asset in $info.assets | Where-Object { $_.name -like "codeling-*.jar" -and $_.name -notlike "*-sources.jar" }) {
        $dest = Join-Path $JarDir $asset.name
        if (-not (Test-Path $dest)) {
            Write-Host "Downloading $($asset.name)"
            Save-File $asset.browser_download_url $dest
        }
    }
}

function Find-CodelingJar([string]$Loader, [string]$Target) {
    $pattern = "codeling-$Loader-$Target-*.jar"
    $dirs = if ($JarDir) { @($JarDir) } else { @(Join-Path $RepoRoot "$($Loader.ToLower())\versions\$Target\build\libs") }
    foreach ($dir in $dirs) {
        if (-not (Test-Path $dir)) { continue }
        $jar = Get-ChildItem $dir -Filter $pattern |
            Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -match "^codeling-$Loader-$([regex]::Escape($Target))-[^-]+\.jar$" } |
            Sort-Object LastWriteTime | Select-Object -Last 1
        if ($jar) { return $jar.FullName }
    }
    return $null
}

Write-Host "Reading Fabric API and NeoForge versions"
$fabricApiVersions = Get-MavenVersions "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
$neoForgeVersions = Get-MavenVersions "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"

function Get-FabricApi([string]$Mc) {
    $v = $fabricApiVersions | Where-Object { $_ -like "*+$Mc" } | Sort-Object { Get-VersionKey $_ } | Select-Object -Last 1
    return $v
}

# NeoForge numbers follow Minecraft: 1.21.8 is 21.8.x, 26.1 is 26.1.0.x, 26.1.2 is 26.1.2.x. Prefer a stable build;
# 1.21.2, 1.21.6, 1.21.7 and 1.21.9 only ever had betas.
function Get-NeoForge([string]$Mc) {
    $parts = $Mc -split '\.'
    if ($parts[0] -eq "1") { $prefix = "21.$(if ($parts.Count -gt 2) { $parts[2] } else { '0' })." }
    else { $prefix = "$($parts[0]).$($parts[1]).$(if ($parts.Count -gt 2) { $parts[2] } else { '0' })." }
    $all = $neoForgeVersions | Where-Object { $_.StartsWith($prefix) }
    $stable = $all | Where-Object { $_ -notmatch '-' }
    $pick = if ($stable) { $stable } else { $all }
    return ($pick | Sort-Object { Get-VersionKey $_ } | Select-Object -Last 1)
}

$cacheDir = Join-Path $MultiMC "codeling-jars\fabric-api"
New-Item -ItemType Directory -Force $cacheDir | Out-Null

# ---------------------------------------------------------------------------------------------------------------
# Instances

function New-Pack([string]$Loader, [string]$Mc, [string]$LoaderVer) {
    $components = @(@{ uid = "net.minecraft"; version = $Mc; important = $true })
    if ($Loader -eq "Fabric") {
        $components += @{ uid = "net.fabricmc.intermediary"; version = $Mc; dependencyOnly = $true }
        $components += @{ uid = "net.fabricmc.fabric-loader"; version = $LoaderVer }
    } else {
        $components += @{ uid = "net.neoforged"; version = $LoaderVer }
    }
    return (@{ formatVersion = 1; components = $components } | ConvertTo-Json -Depth 5)
}

# instance.cfg: keep what MultiMC or the player wrote, set only our keys.
function Set-InstanceConfig([string]$Path, [hashtable]$Values) {
    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path $Path) { foreach ($l in Get-Content $Path) { $lines.Add($l) } }
    foreach ($key in $Values.Keys) {
        $entry = "$key=$($Values[$key])"
        $i = -1
        for ($n = 0; $n -lt $lines.Count; $n++) { if ($lines[$n].StartsWith("$key=")) { $i = $n; break } }
        if ($i -ge 0) { $lines[$i] = $entry } else { $lines.Add($entry) }
    }
    Write-Utf8 $Path (($lines -join "`n") + "`n")
}

$made = New-Object System.Collections.Generic.List[string]
$missingJars = New-Object System.Collections.Generic.List[string]

foreach ($t in $Targets) {
    foreach ($loader in "Fabric", "NeoForge") {
        $jar = Find-CodelingJar $loader.ToLower() $t.Target
        foreach ($mc in $t[$loader]) {
            $name = "Codeling $loader $mc"
            if ($name -notlike $Only) { continue }

            $loaderVer = if ($loader -eq "Fabric") { $loaderVersion } else { Get-NeoForge $mc }
            if (-not $loaderVer) { Write-Warning "${name}: no NeoForge build found for $mc, skipped."; continue }

            $dir = Join-Path $instancesDir $name
            $mods = Join-Path $dir ".minecraft\mods"
            New-Item -ItemType Directory -Force $mods | Out-Null

            $major = if ($mc.StartsWith("1.")) { 21 } else { 25 }
            $cfg = @{ InstanceType = "OneSix"; name = $name; iconKey = "default"
                      notes = "Codeling test instance (scripts/setup-multimc.ps1). Codeling jar for $($t.Target)." }
            if ($java[$major]) { $cfg["OverrideJavaLocation"] = "true"; $cfg["JavaPath"] = $java[$major].Replace('\', '/') }
            Set-InstanceConfig (Join-Path $dir "instance.cfg") $cfg
            Write-Utf8 (Join-Path $dir "mmc-pack.json") (New-Pack $loader $mc $loaderVer)

            # Mods: our jar and Fabric API, replacing older copies; other mods in the folder stay.
            Get-ChildItem $mods -Filter "codeling-*.jar" | Remove-Item -Force
            if ($jar) { Copy-Item $jar $mods }
            else { $missingJars.Add("$name (needs codeling-$($loader.ToLower())-$($t.Target)-*.jar)") }

            $extra = ""
            if ($loader -eq "Fabric") {
                $api = Get-FabricApi $mc
                if ($api) {
                    $apiJar = Join-Path $cacheDir "fabric-api-$api.jar"
                    if (-not (Test-Path $apiJar)) {
                        Save-File "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$api/fabric-api-$api.jar" $apiJar
                    }
                    Get-ChildItem $mods -Filter "fabric-api-*.jar" | Remove-Item -Force
                    Copy-Item $apiJar $mods
                    $extra = ", Fabric API $api"
                } else { Write-Warning "${name}: no Fabric API for $mc found." }
            }
            $jarText = if ($jar) { Split-Path -Leaf $jar } else { "NO CODELING JAR" }
            Write-Host ("{0,-28} {1} {2}, Java {3}, {4}{5}" -f $name, $loader, $loaderVer, $major, $jarText, $extra)
            $made.Add($name)
        }
    }
}

# Group: put every instance made here into $Group and take it out of the other groups.
$groupsPath = Join-Path $instancesDir "instgroups.json"
$groups = [ordered]@{}
if (Test-Path $groupsPath) {
    $old = Get-Content $groupsPath -Raw | ConvertFrom-Json
    foreach ($p in $old.groups.PSObject.Properties) {
        $groups[$p.Name] = [ordered]@{ hidden = [bool]$p.Value.hidden; instances = @($p.Value.instances | Where-Object { $made -notcontains $_ }) }
    }
}
$keep = if ($groups.Contains($Group)) { @($groups[$Group].instances) } else { @() }
$groups[$Group] = [ordered]@{ hidden = $false; instances = @($keep + $made | Sort-Object -Unique) }
Write-Utf8 $groupsPath ([ordered]@{ formatVersion = "1"; groups = $groups } | ConvertTo-Json -Depth 5)

Write-Host ""
Write-Host "$($made.Count) instances in group '$Group' in $instancesDir."
if ($missingJars.Count -gt 0) {
    Write-Warning "No Codeling jar for $($missingJars.Count) instances (build those targets or pass -JarDir / -Release, then run again):"
    $missingJars | ForEach-Object { Write-Host "  $_" }
}
Write-Host "Start MultiMC; it downloads Minecraft and the loaders on each instance's first launch."
