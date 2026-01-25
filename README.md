# Server Maintainer by woflo

A hands-off Minecraft server updater. Drop it in your server folder, run it, and forget about it. It keeps your Minecraft version, mods, plugins, and datapacks up to date automatically.

## Features

- **Auto-updates Minecraft** - Detects and installs new stable versions
- **Auto-updates mods/plugins/datapacks** - Pulls latest versions from Modrinth
- **Multi-loader support** - Fabric, Quilt, Forge, NeoForge, Vanilla
- **Smart compatibility checking** - Only updates Minecraft when enough mods support it
- **Automatic backups** - Creates backups before updates, auto-cleans old ones
- **Startup verification** - Tests that the server actually starts after updates
- **Crash recovery** - Auto-restarts on crash with rate limiting
- **Interactive mode** - Optional prompts before major changes
- **Dry run mode** - Preview changes without applying them

## Quick Start

1. Set up your Minecraft server normally (Fabric, Forge, etc.)
2. Drop `server maintainer by woflo.jar` in your server folder
3. Run it instead of your server JAR, or run.bat:
   ```
   java -jar "server maintainer by woflo.jar"
   ```
   (note: double clicking opens a console)

That's it. It will check for updates, apply them, verify everything works, then start your server. 

## Command Line Options

```
-d, --dry-run      Preview changes without applying
-u, --update-only  Update only, don't start server
-r, --rollback     Restore most recent backup
-i, --interactive  Prompt before updates
-y, --yes          Skip prompts (overrides config)
-h, --help         Show help
```

## Configuration

On first run, creates `woflo/woflo.yml`:

```yaml
# Memory allocation
memory:
  min: 2G
  max: 6G

# What to auto-update
updates:
  minecraft: true        # Minecraft + ModLoader
  mods: true             # Mods from Modrinth
  plugins: true          # Plugins from Modrinth
  datapacks: false       # Datapacks from Modrinth
  min-compatibility: 60  # Only update MC if this % of mods support it
  allow-snapshots: false # Include Minecraft snapshots
  allow-beta: false      # Include beta versions (mods/plugins/datapacks)

# Startup verification timeout (seconds)
startup-timeout: 90

# Prompt before updates (override with -i or -y flags)
interactive: false

# Crash handling
restart-delay: 5      # Seconds before restart
max-crashes: 3        # Max crashes before cooldown
crash-window: 300     # Crash tracking window (seconds)

# Optional overrides
# target-version: 1.21.0  # Lock to specific MC version
# server-jar: server.jar  # Override JAR auto-detection
```

## How It Works

1. **Detects your setup** - Finds your mod loader (Fabric/Forge/etc.) and current version
2. **Checks for updates** - Queries Mojang API and Modrinth for new versions
3. **Creates backup** - Backs up mods, loader files, and version info
4. **Applies updates** - Downloads and installs new versions
5. **Verifies startup** - Boots the server briefly to confirm it works
6. **Starts server** - Runs your server with auto-restart on crash

## Skipping Specific Mods

After running once, `mods/mods.txt` (or `plugins/plugins.txt`, `datapacks/datapacks.txt`) file will be created. Prefix filenames with `#` to skip updates:

## Backups

- Stored in `woflo/backups/`
- Named by timestamp and reason (e.g., `20250125-143022_mc`)
- Auto-cleaned after 7 days
- Use `--rollback` to restore the most recent one

## Requirements

- Java 21+
- An existing Minecraft server setup

## Supported Platforms

- Windows
- Linux
- macOS

## License

GPL-3.0
