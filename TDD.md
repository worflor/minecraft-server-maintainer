# Technical Design Document (TDD)
## Server Maintainer by woflo

**Version:** 1.0.1
**Last Updated:** 2026-01-25
**Architecture:** Zero-dependency Java 21+ CLI Application

---

## Table of Contents
1. [Philosophy & Design Principles](#philosophy--design-principles)
2. [System Architecture](#system-architecture)
3. [Component Deep Dive](#component-deep-dive)
4. [Data Flow](#data-flow)
5. [External API Integration](#external-api-integration)
6. [File System Layout](#file-system-layout)
7. [Threading Model](#threading-model)
8. [Error Handling Strategy](#error-handling-strategy)
9. [Security Considerations](#security-considerations)

---

## Philosophy & Design Principles

### Core Philosophy: "Drop and Forget"
The tool embodies a "set it and forget it" approach. Users should be able to:
1. Drop the JAR in their server folder
2. Run it once
3. Never think about updates again

### Design Principles

**1. Zero Dependencies**
- No external libraries (no Maven, Gradle, or third-party JARs)
- Custom JSON parser built into `Http.java`
- Custom YAML parser built into `Config.java`
- Uses only Java 21+ standard library

**2. Non-Destructive by Default**
- Creates backups before any modification
- Verifies server startup after updates
- Auto-rollback on failure
- Old backups auto-cleaned after 7 days

**3. Smart Defaults, Full Control**
- Works out-of-the-box with sensible defaults
- Every behavior can be overridden via config or flags
- Interactive mode available for cautious users

**4. Platform Agnostic**
- Windows, Linux, macOS support
- Handles both JAR-based and script-based launchers (Forge/NeoForge)
- Auto-detects mod loader from existing setup

**5. Rate-Limit Respectful**
- Honors HTTP 429 responses with exponential backoff
- Uses User-Agent header for API identification
- Parallel requests bounded by virtual thread executor

---

## System Architecture

### High-Level Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Main.java (Entry Point)                  │
│  - CLI argument parsing                                         │
│  - Working directory detection                                  │
│  - Server lifecycle management (crash recovery loop)            │
└─────────────────────────────────────────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    ▼                         ▼
┌──────────────────────────────┐  ┌──────────────────────────────┐
│       Config.java            │  │       Console.java           │
│  - YAML parsing              │  │  - ANSI terminal UI          │
│  - Default generation        │  │  - Progress bars             │
│  - Memory/JVM settings       │  │  - Logging to file           │
└──────────────────────────────┘  └──────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Updater.java (Core Logic)                  │
│  - Update orchestration                                         │
│  - Interactive prompts                                          │
│  - Startup verification                                         │
└─────────────────────────────────────────────────────────────────┘
           │              │              │              │
           ▼              ▼              ▼              ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Loader.java  │  │   Api.java   │  │ Backup.java  │  │ModScanner.java│
│ - Detection  │  │ - Modrinth   │  │ - Create     │  │ - JAR parsing │
│ - Install    │  │ - Version    │  │ - Restore    │  │ - Skip lists  │
│ - JAR lookup │  │   checking   │  │ - Cleanup    │  │ - ID extract  │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
                         │
                         ▼
             ┌──────────────────────┐
             │      Http.java       │
             │  - HTTP client       │
             │  - JSON parser       │
             │  - File downloads    │
             │  - Hash verification │
             └──────────────────────┘
```

### File Count & Line Metrics

| File | Lines | Purpose |
|------|-------|---------|
| Main.java | 142 | Entry, CLI, server lifecycle |
| Updater.java | 225 | Core update orchestration |
| Loader.java | 161 | Mod loader abstraction |
| Config.java | 175 | Configuration parsing |
| Console.java | 101 | Terminal UI/logging |
| Http.java | 159 | HTTP client + JSON parser |
| Api.java | 61 | Modrinth API wrapper |
| Backup.java | 145 | Backup/restore + stasis snapshots |
| ModScanner.java | 65 | Mod file scanning |
| **Total** | **~1234** | |

---

## Component Deep Dive

### Main.java (Lines 1-142)

**Responsibilities:**
- Entry point and CLI argument parsing
- Working directory auto-detection
- Console/logging initialization
- Server process lifecycle with crash recovery

**Key Methods:**

| Method | Lines | Description |
|--------|-------|-------------|
| `main(String[] args)` | 13-37 | Entry point. Parses flags, detects loader, runs updater, starts server |
| `doRollback()` | 39-50 | Finds latest backup and restores it |
| `runServer()` | 52-80 | Server process loop with crash detection and rate limiting |
| `checkEula()` | 82-95 | Ensures EULA is accepted before starting |
| `relaunch()` | 97-107 | Opens new terminal window if double-clicked |
| `printHelp()` | 109-126 | Prints CLI help text |

**CLI Flags:**

| Flag | Variable | Effect |
|------|----------|--------|
| `-d`, `--dry-run` | `dryRun` | Preview mode, no changes |
| `-u`, `--update-only` | `updateOnly` | Update without starting server |
| `-r`, `--rollback` | `rollback` | Restore most recent backup |
| `-i`, `--interactive` | `interactiveFlag = true` | Force prompts |
| `-y`, `--yes` | `interactiveFlag = false` | Skip prompts (override config) |

**Crash Recovery Algorithm (Lines 66-79):**
```
crashes[] = ring buffer of crash timestamps
window = crashWindow (default 300s = 5 min)
maxCrashes = 3

On server exit:
  if exit == 0 OR runtime > window:
    reset crashes[]
    if stasisEnabled && stasisDue: createStasis()
    show countdown animation (restartDelay seconds)
    restart
  else:
    record crash timestamp
    count recent crashes (within window)
    if recent >= maxCrashes:
      enter cooldown (wait crashWindow)
    else:
      show countdown animation (restartDelay seconds)
      restart
```

---

### Updater.java (Lines 1-225)

**Responsibilities:**
- Orchestrates entire update process
- Manages interactive confirmation prompts
- Coordinates parallel API calls
- Verifies server startup after updates

**Key Fields:**

| Field | Type | Purpose |
|-------|------|---------|
| `dir` | Path | Server root directory |
| `cfg` | Config | Parsed configuration |
| `con` | Console | UI/logging interface |
| `dry` | boolean | Dry run mode |
| `loader` | Loader | Detected mod loader |
| `interactive` | boolean | Prompt before actions |

**Key Methods:**

| Method | Lines | Description |
|--------|-------|-------------|
| `run()` | 34-90 | Main update orchestration |
| `confirm(String)` | 19-32 | Interactive confirmation with cursor handling |
| `updateContent(...)` | 97-112 | Generic content update (mods/plugins/datapacks) |
| `runParallel(...)` | 114-121 | Virtual thread parallel executor |
| `checkCompat(...)` | 123-128 | Calculate mod compatibility percentage |
| `verify()` | 130-151 | Boot server briefly to confirm it works |
| `detectVersion()` | 166-172 | Find current MC version from files |

**Update Flow (run method):**

```
1. Check Java version >= 21
2. Check disk space >= 500MB
3. Initialize directories, cleanup old backups
4. Detect current Minecraft version
   └─ If no version: install fresh

5. Display header with loader and version
6. Setup progress rows for each update type

7. Check Minecraft update:
   ├─ Get latest from Mojang API (or use target-version)
   ├─ If loader not ready for version: skip
   ├─ Check mod compatibility percentage
   ├─ If < min-compatibility: block update
   ├─ If interactive: prompt user
   ├─ Create backup
   └─ Install new version

8. For each content type (mods, plugins, datapacks):
   ├─ Scan directory for items
   ├─ Check each item against Modrinth API (parallel)
   ├─ If interactive: prompt before updates
   └─ Download updates with hash verification

9. If any updates applied:
   └─ Verify server startup
      ├─ On success: continue to server launch
      └─ On failure: auto-rollback from backup

10. Display countdown animation, start server
```

**Virtual Thread Executor Pattern (Lines 114-121):**
```java
try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
    var futures = items.stream()
        .map(i -> ex.submit(() -> fn.apply(i)))
        .toList();
    for (var f : futures) {
        res.add(f.get());
        con.rowProgress(row, ++done, total);
    }
}
```

---

### Loader.java

**Responsibilities:**
- Enum representing mod loader types
- Auto-detection from server files
- Mod loader installation
- Server JAR discovery
- Mod ID extraction from JARs

**Enum Values:**

| Loader | Detection Method | Server JAR |
|--------|------------------|------------|
| FABRIC | `fabric-server-launch.jar`, `.fabric/`, or `fabric.mod.json` in mods | `fabric-server-launch.jar` |
| QUILT | `quilt.mod.json` in mods | `quilt-server-launch.jar` |
| FORGE | `forge` in JAR name, or `META-INF/mods.toml` in mods | `run.bat`/`run.sh` |
| NEOFORGE | `META-INF/neoforge.mods.toml` in mods | `run.bat`/`run.sh` |
| PAPER | Paperclip manifest in JAR, or `plugins/` folder | `paper.jar` |
| PURPUR | Paperclip manifest + `purpur` in filename | `purpur.jar` |
| FOLIA | Paperclip manifest + `folia` in filename | `folia.jar` |
| VANILLA | `server.jar` exists and mods folder empty/missing | `server.jar` |

**Key Methods:**

| Method | Lines | Description |
|--------|-------|-------------|
| `detect(Path)` | 11-26 | Auto-detect loader from server files |
| `serverJar()` | 40 | Return expected server JAR name |
| `findServerJar(Path)` | 42-61 | Smart JAR discovery with error on ambiguity |
| `displayName()` | 62 | Human-readable name |
| `modrinthLoaders()` | 63 | Modrinth API loader identifiers (with fallbacks) |
| `ignoredModIds()` | 64 | Built-in mod IDs to skip |
| `backupItems()` | 65 | Files/folders to backup |
| `isReady(String)` | 67-75 | Check if loader supports MC version |
| `getLatestSupported(bool)` | 78-91 | Get latest MC version loader supports |
| `install(String, Path, Console)` | 93-101 | Install loader for MC version |
| `readModId(Path)` | 142-151 | Extract mod ID from JAR |

**Modrinth Loader Mappings:**
```java
FABRIC   → ["fabric", "quilt"]           // Fabric mods, fallback to Quilt
QUILT    → ["quilt", "fabric"]           // Quilt mods, fallback to Fabric
FORGE    → ["forge", "neoforge"]         // Forge mods, fallback to NeoForge
NEOFORGE → ["neoforge", "forge"]         // NeoForge mods, fallback to Forge
PAPER    → ["paper", "spigot", "bukkit"] // Paper plugins
PURPUR   → ["paper", "spigot", "bukkit"] // Purpur plugins (Paper-compatible)
FOLIA    → ["paper", "spigot", "bukkit"] // Folia plugins (Paper-compatible)
VANILLA  → []                            // No mod support
```

**Smart JAR Discovery Algorithm (Lines 42-61):**
```
1. Check for expected JAR name (e.g., fabric-server-launch.jar)
2. If not found, search for JARs containing loader name
3. Filter out installers (contains "installer")
4. If exactly 1 match: use it
5. If multiple matches: throw error with list of conflicts
6. For Forge/NeoForge: check for run.bat/run.sh scripts
7. Fall back to expected name
```

---

### Config.java (Lines 1-175)

**Responsibilities:**
- Parse YAML configuration without external libraries
- Generate default config on first run
- Provide typed access to settings

**Fields:**

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `memoryMin` | String | "2G" | Minimum heap size |
| `memoryMax` | String | "6G" | Maximum heap size |
| `jvmArgs` | List<String> | [see below] | JVM arguments |
| `updateMinecraft` | boolean | true | Auto-update MC |
| `updateMods` | boolean | true | Auto-update mods |
| `updatePlugins` | boolean | true | Auto-update plugins |
| `updateDatapacks` | boolean | false | Auto-update datapacks |
| `allowSnapshots` | boolean | false | Include MC snapshots |
| `allowBeta` | boolean | false | Include beta versions (global) |
| `interactive` | boolean | false | Prompt before updates |
| `minCompatibility` | int | 90 | Min % mods must support new MC |
| `startupTimeout` | int | 90 | Seconds to wait for startup verify |
| `restartDelay` | int | 5 | Seconds before restart |
| `maxCrashes` | int | 3 | Max crashes before cooldown |
| `crashWindow` | int | 300 | Crash tracking window (seconds) |
| `targetVersion` | String | null | Lock to specific MC version |
| `serverJar` | String | null | Override JAR auto-detection |
| `stasisEnabled` | boolean | false | Enable full server snapshots |
| `stasisInterval` | int | 24 | Hours between stasis snapshots |
| `stasisKeep` | int | 3 | Number of stasis snapshots to keep |

**Default JVM Arguments:**
```java
List.of(
    "-Djava.awt.headless=true",
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:+DisableExplicitGC",
    "-XX:+AlwaysPreTouch"
)
```

**YAML Parser Algorithm (Lines 71-98):**
```
For each line:
  1. Skip empty lines and comments (#)
  2. Calculate indent level (spaces before content)
  3. Handle list items (- prefix)
  4. Parse key:value pairs
  5. Strip inline comments
  6. Handle quoted values
  7. Track current section for nested keys
```

---

### Console.java (Lines 1-98)

**Responsibilities:**
- ANSI terminal output with colors and box drawing
- Progress bar rendering with live updates
- Logging to file with timestamps
- Cursor visibility control

**ANSI Escape Sequences:**

| Variable | Code | Purpose |
|----------|------|---------|
| R | `\u001B[0m` | Reset |
| P | `\u001B[35m` | Purple/Magenta |
| G | `\u001B[32m` | Green |
| Y | `\u001B[33m` | Yellow |
| E | `\u001B[31m` | Red (Error) |
| A | `\u001B[90m` | Gray (Dim) |
| W | `\u001B[97m` | White (Bright) |
| UP | `\u001B[A` | Cursor up |
| CL | `\u001B[2K` | Clear line |

**Box Drawing Characters:**

| Variable | Char | Purpose |
|----------|------|---------|
| H | `─` | Horizontal line |
| V | `│` | Vertical line |
| TL | `┌` | Top-left corner |
| TR | `┐` | Top-right corner |
| BL | `└` | Bottom-left corner |
| BR | `┘` | Bottom-right corner |
| CHK | `√` | Checkmark |
| BLK | `█` | Full block (progress) |
| SHD | `░` | Shade block (pending) |

**Key Methods:**

| Method | Lines | Description |
|--------|-------|-------------|
| `header(String, String)` | 36-42 | Draw bordered title box |
| `setupRows(String...)` | 46-50 | Initialize progress rows |
| `rowProgress(int, int, int)` | 63-66 | Update progress bar |
| `rowDone(int, String)` | 68 | Mark row complete |
| `rowDoneUpdate(int, String, String)` | 69 | Show version transition |
| `detail(String, String, String)` | 72-75 | Show individual item update |
| `countdown()` | 84-92 | Draining progress bar (1.5s, post-update) |
| `countdownSeconds(int)` | 93-101 | Configurable countdown (restart delays) |
| `progress(String)` | - | In-place updating status (stasis) |
| `progressDone(String, String)` | - | Complete in-place status with checkmark |

**Terminal Detection (Lines 21-22):**
```java
c = System.getenv("TERM") != null
    || System.getenv("COLORTERM") != null
    || System.getProperty("os.name").toLowerCase().contains("win")
    || System.console() != null;
```

---

### Http.java (Lines 1-157)

**Responsibilities:**
- HTTP GET requests with retry logic
- File downloads with hash verification
- Custom JSON parser (no dependencies)
- Rate limit handling (429 responses)

**HTTP Configuration:**

| Constant | Value | Purpose |
|----------|-------|---------|
| TIMEOUT | 30 seconds | Connection/read timeout |
| USER_AGENT | `woflo/ServerMaintainer/1.0 (github.com/woflo)` | API identification |

**Key Methods:**

| Method | Lines | Description |
|--------|-------|-------------|
| `get(String)` | 18 | HTTP GET with 3 retries |
| `getJson(String)` | 33 | GET and parse as JSON object |
| `getJsonArray(String)` | 34 | GET and parse as JSON array |
| `download(String, Path)` | 36 | Download file to path |
| `downloadVerified(...)` | 52-61 | Download with SHA-512 verification |
| `sha512(Path)` | 65-74 | Calculate file hash |
| `parse(String)` | 80 | Parse JSON string |

**Rate Limit Handling (Lines 24-28):**
```java
if (res.statusCode() == 429 && retries > 0) {
    int wait = Math.min(
        res.headers().firstValue("Retry-After")
            .map(Integer::parseInt).orElse(5),
        60
    );
    Thread.sleep(wait * 1000L);
    return get(url, retries - 1);
}
```

**JSON Parser (Lines 76-149):**
- Recursive descent parser
- Supports: objects, arrays, strings, numbers, booleans, null
- Handles escape sequences (`\n`, `\t`, `\r`, `\"`, `\\`, `\/`, `\uXXXX`)
- Returns `Map<String, Object>` for objects, `List<Object>` for arrays

**JSON Utility Methods (Lines 152-156):**

| Method | Purpose |
|--------|---------|
| `str(Map, String)` | Get string value |
| `bool(Map, String, boolean)` | Get boolean with default |
| `obj(Map, String)` | Get nested object |
| `arr(Map, String)` | Get array of objects |
| `list(Map, String)` | Get array of any type |

---

### Api.java (Lines 1-59)

**Responsibilities:**
- Modrinth API integration
- Mojang version manifest queries
- Mod version checking and comparison

**Key Types:**

```java
record CheckResult(
    String id,           // Mod ID
    Path path,           // Local file path
    String status,       // "update", "current", "skip"
    String oldVersion,   // Installed version
    String newVersion,   // Available version
    String downloadUrl,  // Download URL
    String sha512,       // File hash
    String fileName      // Target filename
)
```

**Loader Constants:**
```java
PLUGIN_LOADERS   = ["paper", "spigot", "bukkit", "folia"]
DATAPACK_LOADERS = ["datapack"]
```

**Key Methods:**

| Method | Lines | Description |
|--------|-------|-------------|
| `getLatestMinecraft(boolean)` | 8-13 | Get latest MC version from Mojang |
| `checkMod(Mod, String, Loader, boolean)` | 20 | Check mod for updates |
| `checkMod(Mod, String, List, boolean)` | 22-58 | Full mod check implementation |

**Mod Check Algorithm (Lines 22-58):**
```
1. Calculate SHA-512 of local mod file
2. Query Modrinth: /v2/version_file/{hash}
   └─ If 404: mod not on Modrinth, return "skip"
3. Get project ID and current version
4. For each loader (with fallbacks):
   └─ Query: /v2/project/{id}/version?game_versions=["mc"]&loaders=["loader"]
5. Find first matching version:
   └─ "release" type, or "beta" if allowBeta
6. Compare versions:
   ├─ Same version: return "current"
   └─ Different: return "update" with download info
```

---

### Backup.java (Lines 1-145)

**Responsibilities:**
- Create incremental backups before updates
- Restore from backup on failure
- Auto-cleanup old backups
- Create full stasis snapshots (compressed ZIP)
- Manage stasis retention

**Backup Naming:**
```
woflo/backups/YYYYMMDD-HHmmss_reason/
  └─ reason: "mc" for Minecraft updates
```

**Key Methods:**

| Method | Lines | Description |
|--------|-------|-------------|
| `create(Path, Loader, String, Console)` | 18-30 | Create timestamped backup |
| `restore(Path, Path, Loader, Console)` | 32-50 | Restore backup with rollback on error |
| `cleanup(Path, int)` | 52-62 | Delete backups older than N days |
| `stasisDue(Path, int)` | 82-89 | Check if stasis snapshot needed |
| `createStasis(Path, Console)` | 91-113 | Create full compressed snapshot |
| `cleanupStasis(Path, int)` | 131-138 | Retain only N most recent stasis |
| `del(Path)` | 72-78 | Recursive directory delete |

**Backup Contents (per Loader):**

| Loader | Backed Up Items |
|--------|-----------------|
| FABRIC | mods/, versions/, libraries/, fabric-server-launch.jar, current_version.txt |
| QUILT | mods/, versions/, libraries/, quilt-server-launch.jar, current_version.txt |
| FORGE | mods/, libraries/, run.bat, run.sh, current_version.txt |
| NEOFORGE | mods/, libraries/, run.bat, run.sh, current_version.txt |
| PAPER | plugins/, paper.jar, current_version.txt |
| PURPUR | plugins/, purpur.jar, current_version.txt |
| FOLIA | plugins/, folia.jar, current_version.txt |
| VANILLA | server.jar, current_version.txt |

**Restore Safety (Lines 26-41):**
```
1. For each backup item:
   a. Rename existing → .old suffix
   b. Copy backup → destination
2. If all successful:
   └─ Delete all .old files
3. If any copy fails:
   └─ Restore all .old files to original names
```

---

### ModScanner.java (Lines 1-61)

**Responsibilities:**
- Scan directories for mods/plugins/datapacks
- Extract mod IDs from JAR files
- Manage skip lists (mods.txt, plugins.txt, datapacks.txt)

**Scan Configurations:**

| Config | Extension | List File | Header |
|--------|-----------|-----------|--------|
| MODS | .jar | mods.txt | Mod List |
| PLUGINS | .jar | plugins.txt | Plugin List |
| DATAPACKS | .zip | datapacks.txt | Datapack List |

**Key Types:**
```java
record Mod(String id, Path path, String fileName)
record ScanConfig(String ext, String txt, String header)
```

**Key Methods:**

| Method | Lines | Description |
|--------|-------|-------------|
| `scan(Path, Loader)` | 16-31 | Scan mods with ID extraction |
| `scanPlugins(Path)` | 33 | Scan plugins (filename-based ID) |
| `scanDatapacks(Path)` | 34 | Scan datapacks (filename-based ID) |
| `readSkipped(Path, ScanConfig)` | 45-52 | Parse skip list |
| `writeTxt(Path, ScanConfig, List, Set)` | 54-60 | Update list file |

**Skip List Format:**
```
# Server Maintainer - Mod List
# Add # before a filename to skip updates

sodium-fabric-0.5.4.jar
# lithium-fabric-0.12.1.jar    ← Skipped (prefixed with #)
phosphor-fabric-0.8.1.jar
```

**Mod ID Extraction (parallel using virtual threads, Lines 22-27):**
```java
try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
    var futures = jars.stream()
        .map(j -> ex.submit(() -> loader.readModId(j)))
        .toList();
    // Collect results...
}
```

---

## Data Flow

### Startup Sequence

```
                    ┌─────────────────────────────────────┐
                    │          User runs JAR              │
                    └─────────────────────────────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────────┐
                    │    Check if running in console      │
                    │  If not: relaunch in new terminal   │
                    └─────────────────────────────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────────┐
                    │       Parse CLI arguments           │
                    │   --dry-run, --interactive, etc.    │
                    └─────────────────────────────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────────┐
                    │     Detect working directory        │
                    │  (from JAR location or CWD)         │
                    └─────────────────────────────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────────┐
                    │   Initialize Console (UI + log)     │
                    │   Register shutdown hook            │
                    └─────────────────────────────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────────┐
                    │        Load Config.java             │
                    │    (create default if missing)      │
                    └─────────────────────────────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────────┐
                    │      Loader.detect(serverDir)       │
                    │  Check files/mods for loader type   │
                    └─────────────────────────────────────┘
                                      │
              ┌─────────────┬─────────┴─────────┐
              ▼             ▼                   ▼
        ┌──────────┐  ┌──────────┐        ┌──────────┐
        │--rollback│  │ Normal   │        │ --help   │
        │          │  │ update   │        │          │
        └──────────┘  └──────────┘        └──────────┘
              │             │                   │
              ▼             ▼                   ▼
        Restore       Updater.run()        Print help
        backup              │                  exit
                            ▼
                    ┌─────────────────────────────────────┐
                    │        Update completed             │
                    │      --dry-run: exit                │
                    │    --update-only: exit              │
                    │        Normal: start server         │
                    └─────────────────────────────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────────┐
                    │     runServer() - process loop      │
                    │   Restart on crash, rate limit      │
                    └─────────────────────────────────────┘
```

### Update Flow (Updater.run)

```
┌────────────────────────────────────────────────────────────────────┐
│                        CHECK PREREQUISITES                          │
│  Java >= 21 ✓    Disk >= 500MB ✓    Init dirs ✓    Cleanup ✓       │
└────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌────────────────────────────────────────────────────────────────────┐
│                       DETECT CURRENT VERSION                        │
│  Read current_version.txt or scan versions/ folder                  │
│  If none found: install fresh                                       │
└────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌────────────────────────────────────────────────────────────────────┐
│                      CHECK MINECRAFT UPDATE                         │
│                                                                     │
│   ┌──────────────────────────────────────────────────────────────┐ │
│   │ 1. Get latest MC version (Mojang API or target-version)     │ │
│   │ 2. Check if loader supports it (Loader.isReady)             │ │
│   │ 3. Calculate mod compatibility %                             │ │
│   │ 4. If compatible & interactive: confirm()                   │ │
│   │ 5. Create backup                                             │ │
│   │ 6. Install new MC + loader                                   │ │
│   └──────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌────────────────────────────────────────────────────────────────────┐
│                      UPDATE MODS (if enabled)                       │
│                                                                     │
│   ┌──────────────────────────────────────────────────────────────┐ │
│   │  For each .jar in mods/:                       [PARALLEL]    │ │
│   │    1. ModScanner.scan() → get mod IDs                        │ │
│   │    2. Api.checkMod() → query Modrinth                        │ │
│   │    3. Collect results                                        │ │
│   │                                                              │ │
│   │  If updates available & interactive: confirm()              │ │
│   │                                                              │ │
│   │  For each update:                              [PARALLEL]    │ │
│   │    1. Http.downloadVerified() with SHA-512                   │ │
│   │    2. Delete old file                                        │ │
│   └──────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
                                  │
                 ┌────────────────┼────────────────┐
                 ▼                ▼                ▼
          ┌───────────┐    ┌───────────┐    ┌───────────┐
          │  PLUGINS  │    │ DATAPACKS │    │   DONE    │
          │(if exists)│    │(if exists)│    │           │
          └───────────┘    └───────────┘    └───────────┘
                 │                │                │
                 └────────────────┼────────────────┘
                                  │
                                  ▼
┌────────────────────────────────────────────────────────────────────┐
│                      VERIFY SERVER STARTUP                          │
│  (only if changes were made)                                        │
│                                                                     │
│   1. Start server process with 1GB heap                             │
│   2. Monitor output for "Done (" or "For help, type"               │
│   3. Wait up to startupTimeout seconds                              │
│   4. Kill process                                                   │
│                                                                     │
│   If failed: Backup.restore() and exit with error                   │
└────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌────────────────────────────────────────────────────────────────────┐
│                         COUNTDOWN & RETURN                          │
│  Display draining progress bar, return 0                            │
└────────────────────────────────────────────────────────────────────┘
```

---

## External API Integration

### Mojang Launcher Meta API

**Base URL:** `https://launchermeta.mojang.com`

| Endpoint | Purpose |
|----------|---------|
| `/mc/game/version_manifest_v2.json` | Get all MC versions, latest release/snapshot |

**Response Structure:**
```json
{
  "latest": {
    "release": "1.21.4",
    "snapshot": "25w04a"
  },
  "versions": [
    {"id": "1.21.4", "type": "release", "url": "..."},
    ...
  ]
}
```

### Fabric Meta API

**Base URL:** `https://meta.fabricmc.net`

| Endpoint | Purpose |
|----------|---------|
| `/v2/versions/game` | Get supported MC versions |
| `/v2/versions/loader/{mcVersion}` | Check loader availability |
| `/v2/versions/installer` | Get installer download |

### Quilt Meta API

**Base URL:** `https://meta.quiltmc.org`

| Endpoint | Purpose |
|----------|---------|
| `/v3/versions/game` | Get supported MC versions |
| `/v3/versions/loader/{mcVersion}` | Check loader availability |
| `/v3/versions/installer` | Get installer download |

### Forge Promotions API

**Base URL:** `https://files.minecraftforge.net`

| Endpoint | Purpose |
|----------|---------|
| `/net/minecraftforge/forge/promotions_slim.json` | Get recommended/latest per MC |

**Response Structure:**
```json
{
  "promos": {
    "1.20.4-recommended": "49.0.30",
    "1.20.4-latest": "49.0.31"
  }
}
```

### NeoForge Maven API

**Base URL:** `https://maven.neoforged.net`

| Endpoint | Purpose |
|----------|---------|
| `/api/maven/versions/releases/net/neoforged/neoforge` | Get all versions |

**Version Mapping:**
- MC 1.21.4 → NeoForge 21.4.x
- MC 1.20.6 → NeoForge 20.6.x

### PaperMC API

**Base URL:** `https://api.papermc.io/v2/projects`

| Endpoint | Purpose |
|----------|---------|
| `/{project}` | Get project info (paper, folia) |
| `/{project}/versions/{mc}/builds` | Get builds for MC version |
| `/{project}/versions/{mc}/builds/{build}/downloads/{file}` | Download JAR |

### Purpur API

**Base URL:** `https://api.purpurmc.org/v2/purpur`

| Endpoint | Purpose |
|----------|---------|
| `/{mc}` | Check if MC version supported |
| `/{mc}/latest/download` | Download latest build |

### Modrinth API

**Base URL:** `https://api.modrinth.com`

| Endpoint | Purpose |
|----------|---------|
| `/v2/version_file/{sha512}` | Look up mod by file hash |
| `/v2/project/{id}/version` | Get versions for project |

**Query Parameters for version search:**
```
?game_versions=["1.21.4"]
&loaders=["fabric"]
```

**Response Fields Used:**
- `project_id` - Unique project identifier
- `version_number` - Semantic version string
- `version_type` - "release", "beta", or "alpha"
- `files[].url` - Download URL
- `files[].hashes.sha512` - Verification hash
- `files[].filename` - Target filename
- `files[].primary` - Is primary file

---

## File System Layout

```
server/
├── woflo/
│   ├── config.yml                   # Configuration file
│   ├── update.log                   # Update history log
│   ├── .pending                     # Crash recovery marker (if interrupted)
│   ├── backups/
│   │   └── 20250125-143022_mc/      # Backup before MC update
│   └── stasis/
│       └── 20250125-180000.zip      # Full server snapshot (optional)
├── mods/
│   ├── mods.txt                     # Mod list with skip markers
│   ├── sodium-fabric-0.5.4.jar
│   ├── lithium-fabric-0.12.1.jar
│   └── ...
├── plugins/                         # (if hybrid server)
│   └── plugins.txt
├── world/
│   └── datapacks/                   # (if datapacks enabled)
│       └── datapacks.txt
├── versions/                        # (Fabric/Quilt only)
│   └── 1.21.4/
├── libraries/                       # Loader libraries
├── fabric-server-launch.jar         # Server entry point
├── current_version.txt              # Current MC version
├── eula.txt                         # EULA acceptance
├── server.properties                # Server configuration
└── server maintainer by woflo.jar   # This tool
```

---

## Threading Model

### Virtual Threads (Java 21+)

The application uses virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) for:

1. **Mod ID Extraction** (ModScanner.java:22)
   - Parse each JAR file in parallel
   - I/O bound (ZipFile reading)

2. **Modrinth API Queries** (Updater.java:100, 107, 124)
   - Check each mod for updates in parallel
   - Network bound

3. **File Downloads** (Updater.java:107)
   - Download multiple mods simultaneously
   - Network + I/O bound

### Thread Safety Considerations

- **Config**: Immutable after load, safe to share
- **Console**: PrintWriter is thread-safe; ANSI cursor operations use synchronized println
- **Http.client**: HttpClient is thread-safe by design
- **Mod lists**: Collected into ArrayList after parallel processing, then read-only

### Progress Tracking

```java
// Pattern used throughout
int done = 0;
for (var f : futures) {
    try { res.add(f.get()); }
    catch (Exception e) { res.add(null); }
    con.rowProgress(row, ++done, total);
}
```

Progress updates are sequential (after each future completes), even though work is parallel.

---

## Error Handling Strategy

### Philosophy: Fail Safe, Inform User

1. **Never leave server in broken state**
   - Backup before any modification
   - Verify startup after updates
   - Auto-rollback on failure

2. **Graceful degradation**
   - API failures → skip that mod/check
   - Network issues → retry with backoff
   - Unknown loader → default to Fabric

3. **Clear error messages**
   - Console.fail() for fatal errors (red X)
   - Console.warn() for recoverable issues (yellow !)
   - All errors logged to update.log

### Error Recovery Matrix

| Error Type | Action |
|------------|--------|
| Java < 21 | Exit immediately with message |
| Disk < 500MB | Exit with space warning |
| Backup failed | Abort update, exit |
| Loader install failed | Restore backup, exit |
| Mod check failed | Skip mod, continue |
| Download failed | Retry 3x, skip on failure |
| Hash mismatch | Retry, delete on failure |
| Startup verify failed | Restore backup, exit |
| Server crash | Restart with rate limiting |

### Rate Limiting

**Modrinth API:**
```java
if (res.statusCode() == 429) {
    int wait = Math.min(
        Retry-After header or 5s,
        60s max
    );
    Thread.sleep(wait);
    retry();
}
```

**Crash Recovery:**
```java
if (crashes >= maxCrashes within window) {
    wait(crashWindow);  // Cool down
    reset(crashes);
}
```

---

## Security Considerations

### Download Verification

All mod downloads are verified with SHA-512:
```java
public static boolean downloadVerified(String url, Path dest, String hash, int retries) {
    download(url, dest);
    if (sha512(dest).equalsIgnoreCase(hash)) return true;
    Files.deleteIfExists(dest);  // Hash mismatch
    // Retry...
}
```

### No Arbitrary Code Execution

- Installers are official sources only (Fabric/Quilt/Forge/NeoForge)
- Downloads only from Modrinth and official Maven repos
- No user-provided URLs or scripts executed

### File System Access

- Limited to server directory and children
- No parent directory traversal
- Backup restoration validates paths

### Network Security

- HTTPS only (HttpClient enforces this)
- TLS verification enabled by default
- No custom certificate handling

### Sensitive Data

- No credentials stored
- No API keys required
- Mojang/Modrinth APIs are public

---

## Appendix: Complete API Reference

### Main.java

```java
public class Main {
    public static void main(String[] args)  // Entry point
    private static void doRollback()        // Restore latest backup
    private static void runServer()         // Server process loop
    private static void checkEula()         // Verify EULA accepted
    private static boolean relaunch()       // Open new terminal
    private static void printHelp()         // Show CLI help
}
```

### Config.java

```java
public class Config {
    // Fields
    public String memoryMin, memoryMax;
    public List<String> jvmArgs;
    public boolean updateMinecraft, updateMods, updatePlugins, updateDatapacks;
    public boolean allowSnapshots, allowBeta, interactive;
    public int minCompatibility, startupTimeout;
    public int restartDelay, maxCrashes, crashWindow;
    public String targetVersion, serverJar;
    public static final int BACKUP_KEEP_DAYS = 7;
    public boolean stasisEnabled;
    public int stasisInterval, stasisKeep;

    // Methods
    public static Config load(Path dir)     // Load or create config
    private void parse(String content)      // Parse YAML
    private boolean bool(String s)          // Parse boolean
    private int num(String s, int d)        // Parse int with default
}
```

### Console.java

```java
public class Console {
    public Console(File logFile)                     // Initialize
    public void header(String loader, String ver)    // Draw title box
    public void dryRun()                             // Show dry run notice
    public void setupRows(String... labels)          // Init progress rows
    public void rowProgress(int i, int done, int total)  // Update progress
    public void rowDone(int i, String v)             // Mark complete
    public void rowDoneUpdate(int i, String from, String to)  // Show transition
    public void rowSkip(int i, String reason)        // Mark skipped
    public void detail(String name, String from, String to)  // Item detail
    public void blankLine()                          // Print newline
    public void info(String msg)                     // Gray message
    public void warn(String msg)                     // Yellow warning
    public void fail(String msg)                     // Red error
    public void checking(String what)                // "Checking..."
    public void checkDone(String result, boolean ok) // Check result
    public void progress(String msg)                 // In-place updating status
    public void progressDone(String label, String result)  // Complete with checkmark
    public void countdown()                          // Draining bar (1.5s)
    public void countdownSeconds(int seconds)        // Configurable countdown
    public void hideCursor()                         // Hide terminal cursor
    public void showCursor()                         // Show terminal cursor
    public void close()                              // Close log file
}
```

### Updater.java

```java
public class Updater {
    public Updater(Path, Config, Console, boolean, Loader, boolean)
    public int run()                                 // Main update flow
    private boolean confirm(String msg)              // Interactive prompt
    private int skip(int row)                        // Mark row skipped
    private List<Mod> scan(Path d, Loader l)         // Scan mods
    private List<Mod> scanP(Path d)                  // Scan plugins
    private List<Mod> scanD(Path d)                  // Scan datapacks
    private int updateContent(...)                   // Generic content update
    private <T,R> List<R> runParallel(List<T>, Function<T,R>, int)  // Parallel exec
    private int checkCompat(List<Mod>, String, int)  // Calculate compat %
    private boolean verify()                         // Startup verification
    private boolean checkJava()                      // Java >= 21
    private boolean checkDisk()                      // Disk >= 500MB
    private void initDirs()                          // Create directories
    private String detectVersion()                   // Find current MC version
    private void writeVersion(String v)              // Save version to file
    private void cleanOld(String keep)               // Delete old version folders
    private String getWorldName()                    // Read level-name from server.properties
}
```

### Loader.java

```java
public enum Loader {
    FABRIC, FORGE, NEOFORGE, QUILT, PAPER, PURPUR, FOLIA, VANILLA;

    public static Loader detect(Path dir)            // Auto-detect from files
    public String serverJar()                        // Expected JAR name
    public String findServerJar(Path dir)            // Smart JAR discovery
    public String displayName()                      // Human-readable name
    public List<String> modrinthLoaders()            // Modrinth loader IDs
    public Set<String> ignoredModIds()               // Built-in mod IDs
    public String[] backupItems()                    // What to backup
    public boolean isReady(String mc)                // Supports MC version?
    public String getLatestSupported(boolean)        // Latest supported MC
    public boolean install(String mc, Path, Console) // Install loader
    public String readModId(Path jar)                // Extract mod ID
}
```

### Api.java

```java
public class Api {
    public static String getLatestMinecraft(boolean allowSnapshots)
    public static CheckResult checkMod(Mod, String mc, Loader, boolean beta)
    public static CheckResult checkMod(Mod, String mc, List<String> loaders, boolean beta)

    public static final List<String> PLUGIN_LOADERS;
    public static final List<String> DATAPACK_LOADERS;

    public record CheckResult(String id, Path path, String status,
        String oldVersion, String newVersion, String downloadUrl,
        String sha512, String fileName);
}
```

### Backup.java

```java
public class Backup {
    public static Path create(Path serverDir, Loader, String reason, Console)
    public static boolean restore(Path backupDir, Path serverDir, Loader, Console)
    public static void cleanup(Path serverDir, int keepDays)
    public static boolean stasisDue(Path serverDir, int intervalHours)
    public static void createStasis(Path serverDir, Console)
    public static void cleanupStasis(Path serverDir, int keep)
    public static void del(Path p)                   // Recursive delete
}
```

### ModScanner.java

```java
public class ModScanner {
    public record Mod(String id, Path path, String fileName);
    public record ScanConfig(String ext, String txt, String header);

    public static final ScanConfig MODS, PLUGINS, DATAPACKS;

    public static List<Mod> scan(Path dir, Loader)   // Scan mods directory
    public static List<Mod> scanPlugins(Path dir)    // Scan plugins
    public static List<Mod> scanDatapacks(Path dir)  // Scan datapacks
}
```

### Http.java

```java
public class Http {
    // HTTP
    public static String get(String url)
    public static Map<String, Object> getJson(String url)
    public static List<Object> getJsonArray(String url)
    public static void download(String url, Path dest)
    public static boolean downloadVerified(String url, Path dest, String hash, int retries)
    public static String encode(String s)            // URL encode
    public static String sha512(Path file)           // File hash

    // JSON
    public static Object parse(String json)
    public static Map<String, Object> parseObject(String json)
    public static List<Object> parseArray(String json)

    // JSON utilities
    public static String str(Map<String, Object> o, String key)
    public static boolean bool(Map<String, Object> o, String key, boolean def)
    public static Map<String, Object> obj(Map<String, Object> o, String key)
    public static List<Map<String, Object>> arr(Map<String, Object> o, String key)
    public static List<Object> list(Map<String, Object> o, String key)
}
```

---

*End of Technical Design Document*
