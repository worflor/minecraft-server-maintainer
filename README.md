<h1 align="center">Server Maintainer, by woflo</h1>

<p align="center">
  <b>Hands-off Minecraft server updates</b><br>
  Keeps your server, mods, plugins, and datapacks up to date automatically.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21+-orange?logo=openjdk&logoColor=white" alt="Java 21+">
  <img src="https://img.shields.io/badge/Minecraft-1.20+-green?logo=minecraft&logoColor=white" alt="Minecraft">
  <img src="https://img.shields.io/github/license/worflor/minecraft-server-maintainer?color=blue" alt="License">
  <img src="https://img.shields.io/badge/platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey" alt="Platform">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Fabric-supported-brightgreen" alt="Fabric">
  <img src="https://img.shields.io/badge/Quilt-supported-brightgreen" alt="Quilt">
  <img src="https://img.shields.io/badge/Forge-supported-brightgreen" alt="Forge">
  <img src="https://img.shields.io/badge/NeoForge-supported-brightgreen" alt="NeoForge">
  <img src="https://img.shields.io/badge/Vanilla-supported-brightgreen" alt="Vanilla">
</p>

<p align="center">
  <img src="assets/screenshot.webp" alt="Server Maintainer in action" width="600">
</p>

## What It Does

**Replaces your `run.bat`.** Run this instead and it'll check for updates, apply them, then start your server.

Before updating Minecraft it creates a backup. After any update it verifies the server boots. If something breaks after a Minecraft update, it rolls back.

## Quick Start

**Requirements:** Java 21+, an existing Minecraft server

1. Drop `server maintainer by woflo.jar` in your server folder
2. Double-click it

First run creates a config at `woflo/woflo.yml`. After that it just works.

## Features

| Feature | Description |
|---------|-------------|
| Auto-updates | Minecraft, mod loaders, mods, plugins, datapacks |
| Smart updates | Only updates Minecraft when enough mods support it |
| Backups | Created before Minecraft updates, cleaned after 7 days |
| Verification | Tests startup after updates, rolls back if needed |
| Crash recovery | Restarts on crash with rate limiting |
| Dry run | Preview changes with `-d` |

## Usage

```
java -jar "server maintainer by woflo.jar" [options]
```

| Option | Description |
|--------|-------------|
| `-d, --dry-run` | Preview changes without applying |
| `-u, --update-only` | Update only, don't start server |
| `-r, --rollback` | Restore most recent backup |
| `-i, --interactive` | Prompt before updates |
| `-y, --yes` | Skip all prompts |
| `-h, --help` | Show help |

## Configuration

Created at `woflo/woflo.yml` on first run:

```yaml
memory:
  min: 2G
  max: 6G

updates:
  minecraft: true         # Minecraft + mod loader
  mods: true              # Mods from Modrinth
  plugins: true           # Plugins from Modrinth
  datapacks: false        # Datapacks from Modrinth
  min-compatibility: 60   # % of mods needed for MC update
  allow-snapshots: false  # Include MC snapshots
  allow-beta: false       # Include beta mods/plugins

startup-timeout: 90       # Seconds to wait for startup
interactive: false        # Prompt before updates

# Crash recovery
restart-delay: 5          # Seconds before restart
max-crashes: 3            # Crashes before cooldown
crash-window: 300         # Window for counting crashes

# Optional
# target-version: 1.21.0  # Lock to specific MC version
# server-jar: server.jar  # Override auto-detection
```

## Skipping Updates

Works the same for mods, plugins, and datapacks.

After first run, check `mods/mods.txt` (or `plugins/plugins.txt`, `datapacks/datapacks.txt`). Prefix a filename with `#` to skip it:

```
# sodium-0.5.8.jar
lithium-0.12.1.jar
```

## Backups

Created before Minecraft updates. Stored in `woflo/backups/`, named like `20250125-143022_mc`. Cleaned after 7 days.

Restore manually with `--rollback`.

## How It Works

```
┌─────────────────┐
│  Detect Setup   │  Find mod loader, current versions
└────────┬────────┘
         ▼
┌─────────────────┐
│  Check Updates  │  Query Mojang API, Modrinth
└────────┬────────┘
         ▼
┌─────────────────┐
│  Apply Updates  │  Backup (if MC update), download, install
└────────┬────────┘
         ▼
┌─────────────────┐
│  Verify Start   │  Test server boots, rollback if needed
└────────┬────────┘
         ▼
┌─────────────────┐
│  Run Server     │  With crash recovery
└─────────────────┘
```

## License

[GPL-3.0](LICENSE)
