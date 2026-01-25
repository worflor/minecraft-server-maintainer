package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Config {
    public String memoryMin = "2G", memoryMax = "6G";
    public List<String> jvmArgs = new ArrayList<>(List.of(
            "-Djava.awt.headless=true",
            "-XX:+UseG1GC",
            "-XX:+ParallelRefProcEnabled",
            "-XX:+DisableExplicitGC",
            "-XX:+AlwaysPreTouch"));
    public boolean updateMinecraft = true, updateMods = true, updatePlugins = true, updateDatapacks = false;
    public boolean allowSnapshots = false, allowBeta = false;
    public boolean interactive = false;
    public int minCompatibility = 60, startupTimeout = 90;
    public int restartDelay = 5, maxCrashes = 3, crashWindow = 300;
    public String targetVersion = null, serverJar = null;
    public static final int BACKUP_KEEP_DAYS = 7;

    private static final String DEFAULT = """
            # Server Maintainer by woflo :]
            # [ v 1.0.0 ]
            # Edit anytime. Changes apply on next restart.

            # How much memory should be allocated to the server
            memory:
              min: 2G
              max: 6G

            # These update automatically, set to false to prevent that
            updates:
              minecraft: true        # Minecraft + ModLoader update when new stable versions are released
              mods: true             # Mods will update from Modrinth
              plugins: true          # Plugins will update from Modrinth (if plugins/ exists)
              datapacks: false       # Datapacks, from Modrinth (if world/datapacks/ exists)
              min-compatibility: 60  # Only update MC if this % of content support it
              allow-snapshots: false # Include Minecraft snapshots/pre-releases
              allow-beta: false      # Include beta versions (mods/plugins/datapacks)

            # How long to wait when testing server startup (seconds)
            startup-timeout: 90

            # Prompt before updates (override with -i or -y flags)
            interactive: false

            # Restart behavior
            restart-delay: 5      # Seconds to wait before restarting after stop/crash
            max-crashes: 3        # Max crashes before extended cooldown
            crash-window: 300     # Seconds to track crashes (default 5 min)

            # Lock to a specific Minecraft version (leave commented for latest):
            # target-version: 1.21.0

            # Override the server JAR filename (auto-detected if not set):
            # server-jar: server.jar

            # Skip specific items by adding # before their filename in mods.txt, plugins.txt, or datapacks.txt
            """;

    public static Config load(Path dir) throws IOException {
        var wofloDir = dir.resolve("woflo");
        Files.createDirectories(wofloDir);
        var f = wofloDir.resolve("config.yml");
        var c = new Config();
        if (!Files.exists(f)) {
            Files.writeString(f, DEFAULT);
            return c;
        }
        c.parse(Files.readString(f));
        return c;
    }

    private void parse(String content) {
        String section = "", listKey = "";
        boolean jvmCleared = false;
        for (var raw : content.split("\n")) {
            var line = raw.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            int indent = 0;
            for (var ch : raw.toCharArray()) {
                if (ch == ' ')
                    indent++;
                else
                    break;
            }
            if (line.startsWith("- ")) {
                if (listKey.equals("jvm-args")) {
                    if (!jvmCleared) {
                        jvmArgs.clear();
                        jvmCleared = true;
                    }
                    jvmArgs.add(line.substring(2).trim());
                }
                continue;
            }
            int col = line.indexOf(':');
            if (col == -1)
                continue;
            String key = line.substring(0, col).trim(), val = line.substring(col + 1).trim();
            int cmt = val.indexOf('#');
            if (cmt > 0)
                val = val.substring(0, cmt).trim();
            if (val.startsWith("\"") && val.endsWith("\""))
                val = val.substring(1, val.length() - 1);
            if (val.isEmpty()) {
                if (indent == 0)
                    section = key;
                else
                    listKey = key;
                continue;
            }
            switch (section) {
                case "memory" -> {
                    if (key.equals("min"))
                        memoryMin = val;
                    else if (key.equals("max"))
                        memoryMax = val;
                }
                case "updates" -> {
                    switch (key) {
                        case "minecraft" -> updateMinecraft = bool(val);
                        case "mods" -> updateMods = bool(val);
                        case "plugins" -> updatePlugins = bool(val);
                        case "datapacks" -> updateDatapacks = bool(val);
                        case "min-compatibility" -> minCompatibility = num(val, 60);
                        case "allow-snapshots" -> allowSnapshots = bool(val);
                        case "allow-beta" -> allowBeta = bool(val);
                    }
                }
                case "" -> {
                    switch (key) {
                        case "target-version" -> targetVersion = val;
                        case "startup-timeout" -> startupTimeout = num(val, 90);
                        case "server-jar" -> serverJar = val;
                        case "restart-delay" -> restartDelay = num(val, 5);
                        case "max-crashes" -> maxCrashes = num(val, 3);
                        case "crash-window" -> crashWindow = num(val, 300);
                        case "interactive" -> interactive = bool(val);
                    }
                }
            }
        }
    }

    private boolean bool(String s) {
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes");
    }

    private int num(String s, int d) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return d;
        }
    }
}
