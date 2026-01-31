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

## Why...

**not Docker?**
I don't want to learn Docker just to run a Minecraft server. I want to double-click something and have it work. :P

**not Pterodactyl/AMP/Crafty?**
I'm running one, two, or three servers for my friends, not a hosting company. I don't need a web panel, just the trust that its up, and will be up again if someone crashes it.

**not AutoPlug?**
AutoPlug's great and has WAY more features. It's also WAY bigger, has a paid tier, and overall is far bigger than one 67kb jar with one config. Running the same language as the game itself.

I've been running Minecraft servers for years with `run.bat` scripts of various complexities. This tool is the natural evolution of that.

## Quick Start

**Requirements:** Java 21+

1. Drop `server maintainer by woflo.jar` in your server folder
2. Double-click it

*That's it.* Config is created at `woflo/config.yml` on first run. If no server is found, it will download a fresh fabric server.

## Features

- **Auto-updates** - Minecraft, mod loaders (fabric, neoforge...), mods, plugins, datapacks
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
- `-y, --yes` - Skip all prompts in interactive mode
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

# Full server snapshots [on clean stop]
stasis:
  enabled: false
  interval: 24
  keep: 3

# Optional
# target-version: 1.21.0
# server-jar: server_jar_name.jar
```

## Skipping Updates

After first run, text files are created in `woflo/`:

- `woflo/mods.txt`
- `woflo/plugins.txt`
- `woflo/datapacks.txt`

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

- **Detect** - Find server type and current versions
- **Check** - Query Mojang API and Modrinth for updates
- **Backup** - Save current state before updates
- **Update** - Download and install new content
- **Verify** - Test server startup, rollback if needed
- **Run** - Start server with crash recovery

## How I Use It

I have a scheduled startup task on my Server PC that runs every script in a specific folder. In there, I put shortcuts to `run.bat` files for my servers. (now to 'run.jar' with this tool)

That's it. Servers start on boot, restart on crash, and self-maintain over time. I can `/stop` from in-game and it'll be back in 5 seconds. No web UI, no Docker, no panel - it's just a jar.

To get the my old behaviour with this tool, just disable auto updating and, boom. You're back to using a `run.bat` that self restarts on crash. (so, better)

---

<p align="center">
  <a href="docs/FAQ.md">FAQ</a> · <a href="LICENSE">GPL-3.0</a>
</p>
