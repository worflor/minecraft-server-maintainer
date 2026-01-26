<h1 align="center">Server Maintainer, by woflo</h1>

<p align="center">
  <b>Let your server take care of itself</b><br>
  Updates Minecraft, mods, plugins, and datapacks automatically.
</p>

<p align="center">
  <a href="https://woflo.dev"><img src="https://img.shields.io/badge/by-woflo-purple" alt="woflo.dev"></a>
  <img src="https://img.shields.io/badge/Java-21+-orange?logo=openjdk&logoColor=white" alt="Java 21+">
  <img src="https://img.shields.io/badge/Minecraft-1.20+-green?logo=minecraft&logoColor=white" alt="Minecraft">
  <img src="https://img.shields.io/github/license/worflor/minecraft-server-maintainer?color=blue" alt="License">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Fabric-supported-brightgreen" alt="Fabric">
  <img src="https://img.shields.io/badge/Quilt-supported-brightgreen" alt="Quilt">
  <img src="https://img.shields.io/badge/Forge-supported-brightgreen" alt="Forge">
  <img src="https://img.shields.io/badge/NeoForge-supported-brightgreen" alt="NeoForge">
  <img src="https://img.shields.io/badge/Paper-supported-brightgreen" alt="Paper">
  <img src="https://img.shields.io/badge/Purpur-supported-brightgreen" alt="Purpur">
  <img src="https://img.shields.io/badge/Folia-supported-brightgreen" alt="Folia">
  <img src="https://img.shields.io/badge/Vanilla-supported-brightgreen" alt="Vanilla">
</p>

<p align="center">
  <img src="assets/screenshot.webp" alt="Server Maintainer" width="600">
</p>

---

## What It Does

**Replace your `run.bat` with this.** Double-click once and your server handles the rest.

- Checks for updates to Minecraft, mods, plugins, and datapacks
- Creates backups before updating Minecraft
- Verifies your server still starts after updates
- Rolls back automatically if something breaks
- Restarts on crash with rate limiting

## Quick Start

**Requirements:** Java 21+ and an existing Minecraft server

1. Drop `server maintainer by woflo.jar` in your server folder
2. Double-click it

That's it. Config is created at `woflo/config.yml` on first run.

## Features

- **Auto-updates** - Minecraft, mod loaders, mods, plugins, datapacks
- **Smart updates** - Only updates Minecraft when enough mods support it
- **Backups** - Created before Minecraft updates, auto-cleaned after 7 days
- **Verification** - Tests startup after updates, rolls back if needed
- **Crash recovery** - Restarts on crash with rate limiting
- **Stasis snapshots** - Optional full server backups on clean stops
- **Dry run** - Preview changes with `-d` flag

## Command Line

```
java -jar "server maintainer by woflo.jar" [options]
```

**Options:**
- `-d, --dry-run` - Preview changes without applying
- `-u, --update-only` - Update only, don't start server
- `-r, --rollback` - Restore most recent backup
- `-i, --interactive` - Prompt before updates
- `-y, --yes` - Skip all prompts
- `-h, --help` - Show help

## Configuration

First run creates `woflo/config.yml`:

```yaml
memory:
  min: 2G
  max: 6G

updates:
  minecraft: true
  mods: true
  plugins: true
  datapacks: false
  min-compatibility: 90
  allow-snapshots: false
  allow-beta: false

startup-timeout: 90
interactive: false

# Crash recovery
restart-delay: 5
max-crashes: 3
crash-window: 300

# Full server snapshots (on clean stop)
stasis:
  enabled: false
  interval: 24
  keep: 3

# Optional
# target-version: 1.21.0
# server-jar: server.jar
```

## Skipping Updates

After first run, text files are created listing your content:

- `mods/mods.txt`
- `plugins/plugins.txt`
- `datapacks/datapacks.txt`

Add `#` before any filename to skip updates:

```
# sodium-0.5.8.jar
lithium-0.12.1.jar
```

## Backups

**Incremental backups** are stored in `woflo/backups/` before Minecraft updates. Auto-cleaned after 7 days. Run with `--rollback` to restore the latest.

**Stasis snapshots** (optional) create full compressed server archives in `woflo/stasis/` when the server stops cleanly. Enable in config with `stasis.enabled: true`. Great for preserving complete server state between sessions.

## How It Works

<p align="center">
  <img src="assets/demo.gif" alt="Server Maintainer in action" width="600">
</p>

**Detect** - Find mod loader and current versions
**Check** - Query Mojang API and Modrinth for updates
**Backup** - Save current state before Minecraft updates
**Update** - Download and install new versions
**Verify** - Test server startup, rollback if needed
**Run** - Start server with crash recovery

---

<p align="center">
  <a href="LICENSE">GPL-3.0</a>
</p>
