# FAQ

## General

**Does this work with Paper/Forge/Quilt/etc? FOLIA???**

Yes. Fabric, Quilt, Forge, NeoForge, Paper, Purpur, Folia, and Vanilla are all natively supported. It auto-detects what you're running and works with it. 

**Does it update the server jar or just mods?**

Both. It updates Minecraft itself, the mod loader (Fabric/Quilt/etc), mods, plugins, and datapacks.

**What happens if an update breaks my server?**

Before an update, a backup is created, then Minecraft is updated. If the server can't start, it automatically rolls back to the backup. You can also manually rollback with `--rollback`.

**Can I use this on a hosted server or panel?**

Maybe. I genuinely don't know. But also, why..?

---

## Updates

**Where does it check for updates?**

Mods, plugins, and datapacks are checked via [Modrinth](https://modrinth.com). Server jars are fetched from official sources (Mojang, Fabric Meta, Paper API, etc).

**What if my mod isn't on Modrinth?**

It won't update. You'll need to update it manually. The tool will skip anything it can't find.

**Is this system flakey?**

No. The updater goes into each jar to figure out its fingerprint. So even if you rename the mod, datapack, or plugin, its still probably gonna work. The only risk is with Paper, Folia, and Purpur where the devs recommend against auto-updating tools.

**Does it update Minecraft automatically?**

By default, yes. It waits until enough of your mods support the new version (configurable via `min-compatibility`). You can also run with `--interactive` to approve updates manually, or disable Minecraft updates entirely in config.

---

## Safety

**Will it delete my world?**

No. It only touches mods, plugins, datapacks, and server jars. Your world, configs, and other files are left alone.

**What gets backed up?**

Before a Minecraft version update: mods folder, server jar, loader files, and version info. Not the whole server - just what's needed to rollback the update.

---

## Technical

**Why Java 21?**

Minecraft 1.20.5+ requires Java 21. If you're running an older version, you probably have Java 21 already.

**Does it work on [Windows, Linux, macOS]?**

Yes. It's just a jar. Works anywhere Java runs. Even a smart fridge :P

**Can I run it headless / as a service?**

Yes. Thats what I do. 

**What does `min-compatibility` do?**

It's the percentage of your [mods, plugins, datapacks] that need to support a new Minecraft version before it'll update. Default is 90%. Set to 100% if you want all mods ready before updating, or lower if you're okay with some breaking.

**Does it support CurseForge?**

Curseforge's API requires auth and has restrictions that make it annoying to support. Also, Modrinth supports their creators better. If your mod/plugin/datapack is not on Modrinth, you can't update it with this tool.

---

## Troubleshooting

**Server won't start**

Check you have Java 21+. Run `java -version` to confirm.

**Mods aren't updating**

Make sure they're on Modrinth. The tool can only update what it can find.

**It's only downloading fabric**

The tool needs something to detect - a server jar, a mod, or a loader file. If you're starting fresh, it defaults to Fabric.

**Updates keep rolling back**

Your server is failing to start after updates. Check the logs, something's incompatible. You might need to skip a problematic mod. Or maybe, its meeeeeee
