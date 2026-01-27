package dev.woflo.fabric;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Factory for creating test fixtures - sample server directories, mod JARs,
 * plugin JARs, config files, and API responses.
 * <p>
 * These fixtures simulate real Minecraft server environments without requiring
 * actual server files or network access.
 */
public final class TestFixtures {

    private TestFixtures() {
        // Utility class
    }

    // ========================================================================
    // Server Directory Fixtures
    // ========================================================================

    /**
     * Creates a minimal Fabric server directory structure.
     */
    public static Path createFabricServer(Path baseDir) throws IOException {
        Path serverDir = baseDir.resolve("fabric-server");
        Files.createDirectories(serverDir);

        // Fabric launcher JAR (empty but valid)
        createMinimalJar(serverDir.resolve("fabric-server-launch.jar"), Map.of(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: net.fabricmc.loader.launch.server.FabricServerLauncher\n"
        ));

        // Mods directory with sample mod
        Path modsDir = serverDir.resolve("mods");
        Files.createDirectories(modsDir);
        createFabricMod(modsDir.resolve("sodium-fabric-0.5.8.jar"), "sodium", "0.5.8");
        createFabricMod(modsDir.resolve("lithium-fabric-0.12.1.jar"), "lithium", "0.12.1");

        // Version tracking
        Files.writeString(serverDir.resolve("current_version.txt"), "1.21.1");

        // woflo directory
        Path wofloDir = serverDir.resolve("woflo");
        Files.createDirectories(wofloDir);

        return serverDir;
    }

    /**
     * Creates a minimal Quilt server directory structure.
     */
    public static Path createQuiltServer(Path baseDir) throws IOException {
        Path serverDir = baseDir.resolve("quilt-server");
        Files.createDirectories(serverDir);

        createMinimalJar(serverDir.resolve("quilt-server-launch.jar"), Map.of(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: org.quiltmc.loader.impl.launch.server.QuiltServerLauncher\n"
        ));

        Path modsDir = serverDir.resolve("mods");
        Files.createDirectories(modsDir);
        createQuiltMod(modsDir.resolve("qsl-1.0.0.jar"), "qsl", "1.0.0");

        Files.writeString(serverDir.resolve("current_version.txt"), "1.21.1");
        Files.createDirectories(serverDir.resolve("woflo"));

        return serverDir;
    }

    /**
     * Creates a minimal Forge server directory structure.
     */
    public static Path createForgeServer(Path baseDir) throws IOException {
        Path serverDir = baseDir.resolve("forge-server");
        Files.createDirectories(serverDir);

        // Forge uses run scripts
        Files.writeString(serverDir.resolve("run.bat"), "@echo off\njava -jar forge-server.jar nogui");
        Files.writeString(serverDir.resolve("run.sh"), "#!/bin/bash\njava -jar forge-server.jar nogui");

        Path modsDir = serverDir.resolve("mods");
        Files.createDirectories(modsDir);
        createForgeMod(modsDir.resolve("jei-1.21.1-forge.jar"), "jei", "1.0.0");

        Files.writeString(serverDir.resolve("current_version.txt"), "1.21.1");
        Files.createDirectories(serverDir.resolve("woflo"));
        Files.createDirectories(serverDir.resolve("libraries"));

        return serverDir;
    }

    /**
     * Creates a minimal NeoForge server directory structure.
     */
    public static Path createNeoForgeServer(Path baseDir) throws IOException {
        Path serverDir = baseDir.resolve("neoforge-server");
        Files.createDirectories(serverDir);

        Files.writeString(serverDir.resolve("run.bat"), "@echo off\njava -jar neoforge-server.jar nogui");
        Files.writeString(serverDir.resolve("run.sh"), "#!/bin/bash\njava -jar neoforge-server.jar nogui");

        Path modsDir = serverDir.resolve("mods");
        Files.createDirectories(modsDir);
        createNeoForgeMod(modsDir.resolve("create-neoforge-1.0.jar"), "create", "1.0.0");

        Files.writeString(serverDir.resolve("current_version.txt"), "1.21.1");
        Files.createDirectories(serverDir.resolve("woflo"));
        Files.createDirectories(serverDir.resolve("libraries"));

        return serverDir;
    }

    /**
     * Creates a minimal Paper server directory structure.
     */
    public static Path createPaperServer(Path baseDir) throws IOException {
        Path serverDir = baseDir.resolve("paper-server");
        Files.createDirectories(serverDir);

        createPaperJar(serverDir.resolve("paper.jar"), "paper");

        Path pluginsDir = serverDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        createPlugin(pluginsDir.resolve("EssentialsX-2.20.jar"), "Essentials", "2.20.0");
        createPlugin(pluginsDir.resolve("Vault-1.7.jar"), "Vault", "1.7.3");

        Files.writeString(serverDir.resolve("current_version.txt"), "1.21.1");
        Files.createDirectories(serverDir.resolve("woflo"));

        return serverDir;
    }

    /**
     * Creates a minimal Purpur server directory structure.
     */
    public static Path createPurpurServer(Path baseDir) throws IOException {
        Path serverDir = baseDir.resolve("purpur-server");
        Files.createDirectories(serverDir);

        createPaperJar(serverDir.resolve("purpur.jar"), "purpur");

        Path pluginsDir = serverDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        createPlugin(pluginsDir.resolve("LuckPerms-5.4.jar"), "LuckPerms", "5.4.0");

        Files.writeString(serverDir.resolve("current_version.txt"), "1.21.1");
        Files.createDirectories(serverDir.resolve("woflo"));

        return serverDir;
    }

    /**
     * Creates a minimal Folia server directory structure.
     */
    public static Path createFoliaServer(Path baseDir) throws IOException {
        Path serverDir = baseDir.resolve("folia-server");
        Files.createDirectories(serverDir);

        createPaperJar(serverDir.resolve("folia.jar"), "folia");

        Path pluginsDir = serverDir.resolve("plugins");
        Files.createDirectories(pluginsDir);

        Files.writeString(serverDir.resolve("current_version.txt"), "1.21.1");
        Files.createDirectories(serverDir.resolve("woflo"));

        return serverDir;
    }

    /**
     * Creates a minimal Vanilla server directory structure.
     */
    public static Path createVanillaServer(Path baseDir) throws IOException {
        Path serverDir = baseDir.resolve("vanilla-server");
        Files.createDirectories(serverDir);

        createMinimalJar(serverDir.resolve("server.jar"), Map.of(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: net.minecraft.server.Main\n"
        ));

        Files.writeString(serverDir.resolve("current_version.txt"), "1.21.1");
        Files.createDirectories(serverDir.resolve("woflo"));

        return serverDir;
    }

    /**
     * Creates a server directory for the specified loader.
     */
    public static Path createServerForLoader(Path baseDir, Loader loader) throws IOException {
        return switch (loader) {
            case FABRIC -> createFabricServer(baseDir);
            case QUILT -> createQuiltServer(baseDir);
            case FORGE -> createForgeServer(baseDir);
            case NEOFORGE -> createNeoForgeServer(baseDir);
            case PAPER -> createPaperServer(baseDir);
            case PURPUR -> createPurpurServer(baseDir);
            case FOLIA -> createFoliaServer(baseDir);
            case VANILLA -> createVanillaServer(baseDir);
        };
    }

    // ========================================================================
    // Mod/Plugin JAR Fixtures
    // ========================================================================

    /**
     * Creates a Fabric mod JAR with fabric.mod.json metadata.
     */
    public static void createFabricMod(Path jarPath, String modId, String version) throws IOException {
        String fabricModJson = """
                {
                    "schemaVersion": 1,
                    "id": "%s",
                    "version": "%s",
                    "name": "%s",
                    "environment": "*",
                    "entrypoints": {}
                }
                """.formatted(modId, version, capitalize(modId));

        createMinimalJar(jarPath, Map.of(
                "fabric.mod.json", fabricModJson,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a Quilt mod JAR with quilt.mod.json metadata.
     */
    public static void createQuiltMod(Path jarPath, String modId, String version) throws IOException {
        String quiltModJson = """
                {
                    "schema_version": 1,
                    "quilt_loader": {
                        "group": "dev.test",
                        "id": "%s",
                        "version": "%s"
                    }
                }
                """.formatted(modId, version);

        createMinimalJar(jarPath, Map.of(
                "quilt.mod.json", quiltModJson,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a Forge mod JAR with mods.toml metadata.
     */
    public static void createForgeMod(Path jarPath, String modId, String version) throws IOException {
        String modsToml = """
                modLoader="javafml"
                loaderVersion="[47,)"
                license="MIT"

                [[mods]]
                modId="%s"
                version="%s"
                displayName="%s"
                """.formatted(modId, version, capitalize(modId));

        createMinimalJar(jarPath, Map.of(
                "META-INF/mods.toml", modsToml,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a NeoForge mod JAR with neoforge.mods.toml metadata.
     */
    public static void createNeoForgeMod(Path jarPath, String modId, String version) throws IOException {
        String modsToml = """
                modLoader="javafml"
                loaderVersion="[2,)"
                license="MIT"

                [[mods]]
                modId="%s"
                version="%s"
                displayName="%s"
                """.formatted(modId, version, capitalize(modId));

        createMinimalJar(jarPath, Map.of(
                "META-INF/neoforge.mods.toml", modsToml,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a Bukkit/Spigot/Paper plugin JAR with plugin.yml metadata.
     */
    public static void createPlugin(Path jarPath, String pluginName, String version) throws IOException {
        String pluginYml = """
                name: %s
                version: %s
                main: com.example.%s.Main
                api-version: '1.21'
                """.formatted(pluginName, version, pluginName.toLowerCase());

        createMinimalJar(jarPath, Map.of(
                "plugin.yml", pluginYml,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a BungeeCord plugin JAR with bungee.yml metadata.
     */
    public static void createBungeePlugin(Path jarPath, String pluginName, String version) throws IOException {
        String bungeeYml = """
                name: %s
                version: %s
                main: com.example.%s.BungeeMain
                """.formatted(pluginName, version, pluginName.toLowerCase());

        createMinimalJar(jarPath, Map.of(
                "bungee.yml", bungeeYml,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a Velocity plugin JAR with velocity-plugin.json metadata.
     */
    public static void createVelocityPlugin(Path jarPath, String pluginId, String version) throws IOException {
        String velocityJson = """
                {
                    "id": "%s",
                    "name": "%s",
                    "version": "%s",
                    "main": "com.example.%s.VelocityMain"
                }
                """.formatted(pluginId, capitalize(pluginId), version, pluginId);

        createMinimalJar(jarPath, Map.of(
                "velocity-plugin.json", velocityJson,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a datapack ZIP with pack.mcmeta.
     */
    public static void createDatapack(Path zipPath, String packId, String description) throws IOException {
        String packMcmeta = """
                {
                    "pack": {
                        "pack_format": 48,
                        "description": "%s",
                        "id": "%s"
                    }
                }
                """.formatted(description, packId);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write(packMcmeta.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Add empty data directory structure
            zos.putNextEntry(new ZipEntry("data/"));
            zos.closeEntry();
        }
    }

    /**
     * Creates a Paper/Purpur/Folia server JAR with paperclip manifest.
     */
    public static void createPaperJar(Path jarPath, String variant) throws IOException {
        String manifest = """
                Manifest-Version: 1.0
                Main-Class: io.papermc.paperclip.Main
                """;

        createMinimalJar(jarPath, Map.of(
                "META-INF/MANIFEST.MF", manifest
        ));
    }

    // ========================================================================
    // Config File Fixtures
    // ========================================================================

    /**
     * Creates a default woflo config.yml.
     */
    public static void createDefaultConfig(Path configPath) throws IOException {
        String config = """
                # Server Maintainer by woflo :]
                # [ v 1.0.2 ]

                memory:
                  min: 2G
                  max: 6G

                jvm-args:
                  - -Djava.awt.headless=true
                  - -XX:+UseG1GC

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
                restart-delay: 5
                max-crashes: 3
                crash-window: 300

                stasis:
                  enabled: false
                  interval: 24
                  keep: 3
                """;

        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, config);
    }

    /**
     * Creates a config with custom values for testing.
     */
    public static void createCustomConfig(Path configPath, Map<String, Object> overrides) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Test Config\n\n");

        for (var entry : overrides.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, sb.toString());
    }

    /**
     * Creates a mods.txt skip file.
     */
    public static void createSkipFile(Path skipFile, List<String> files, Set<String> skipped) throws IOException {
        StringBuilder sb = new StringBuilder("# Mod List - add # before filename to skip updates\n\n");
        for (String file : files) {
            if (skipped.contains(file)) {
                sb.append("# ");
            }
            sb.append(file).append("\n");
        }
        Files.writeString(skipFile, sb.toString());
    }

    // ========================================================================
    // EULA and Server Properties
    // ========================================================================

    /**
     * Creates an accepted eula.txt file.
     */
    public static void createAcceptedEula(Path serverDir) throws IOException {
        Files.writeString(serverDir.resolve("eula.txt"), """
                #By changing the setting below to TRUE you are indicating your agreement to our EULA
                #https://aka.ms/MinecraftEULA
                eula=true
                """);
    }

    /**
     * Creates a server.properties file.
     */
    public static void createServerProperties(Path serverDir, Map<String, String> properties) throws IOException {
        StringBuilder sb = new StringBuilder("#Minecraft server properties\n");
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("level-name", "world");
        defaults.put("server-port", "25565");
        defaults.put("online-mode", "true");
        defaults.putAll(properties);

        for (var entry : defaults.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }

        Files.writeString(serverDir.resolve("server.properties"), sb.toString());
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Creates a minimal JAR file with the specified entries.
     */
    public static void createMinimalJar(Path jarPath, Map<String, String> entries) throws IOException {
        Files.createDirectories(jarPath.getParent());

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (var entry : entries.entrySet()) {
                jos.putNextEntry(new JarEntry(entry.getKey()));
                jos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
    }

    /**
     * Capitalizes the first letter of a string.
     */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Loads a test resource file as a string.
     */
    public static String loadResource(String resourcePath) throws IOException {
        try (InputStream is = TestFixtures.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Loads a test resource file, returning empty string if not found.
     */
    public static String loadResourceOrEmpty(String resourcePath) {
        try {
            return loadResource(resourcePath);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Computes SHA-512 hash of a file (for testing downloadVerified).
     */
    public static String sha512(Path file) throws IOException {
        return Http.sha512(file);
    }

    /**
     * Creates a complete server setup with common files.
     */
    public static void setupCompleteServer(Path serverDir, Loader loader) throws IOException {
        createAcceptedEula(serverDir);
        createServerProperties(serverDir, Map.of());
        createDefaultConfig(serverDir.resolve("woflo/config.yml"));
    }

    // ========================================================================
    // Edge Case Fixtures
    // ========================================================================

    /**
     * Creates a corrupted/invalid JAR file (not actually a JAR).
     * Useful for testing error handling when JAR parsing fails.
     */
    public static void createCorruptedJar(Path jarPath) throws IOException {
        Files.createDirectories(jarPath.getParent());
        Files.writeString(jarPath, "This is not a valid JAR file - just random text content");
    }

    /**
     * Creates an empty JAR file with only a manifest.
     * Useful for testing handling of JARs without mod metadata.
     */
    public static void createEmptyJar(Path jarPath) throws IOException {
        createMinimalJar(jarPath, Map.of(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a Fabric mod JAR with malformed/invalid JSON.
     * Useful for testing JSON parsing error handling.
     */
    public static void createMalformedFabricMod(Path jarPath) throws IOException {
        createMinimalJar(jarPath, Map.of(
                "fabric.mod.json", "{ this is not valid json :",
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a Fabric mod JAR missing the required 'id' field.
     */
    public static void createFabricModMissingId(Path jarPath, String version) throws IOException {
        String fabricModJson = """
                {
                    "schemaVersion": 1,
                    "version": "%s",
                    "name": "Test Mod",
                    "environment": "*"
                }
                """.formatted(version);

        createMinimalJar(jarPath, Map.of(
                "fabric.mod.json", fabricModJson,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates a Forge mod JAR with empty mods.toml (no [[mods]] section).
     */
    public static void createEmptyForgeMod(Path jarPath) throws IOException {
        String modsToml = """
                modLoader="javafml"
                loaderVersion="[47,)"
                license="MIT"
                # No [[mods]] section
                """;

        createMinimalJar(jarPath, Map.of(
                "META-INF/mods.toml", modsToml,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }

    /**
     * Creates an unaccepted eula.txt file.
     */
    public static void createUnacceptedEula(Path serverDir) throws IOException {
        Files.writeString(serverDir.resolve("eula.txt"), """
                #By changing the setting below to TRUE you are indicating your agreement to our EULA
                #https://aka.ms/MinecraftEULA
                eula=false
                """);
    }

    /**
     * Creates a config.yml with all updates disabled.
     */
    public static void createNoUpdatesConfig(Path configPath) throws IOException {
        String config = """
                # Test config with all updates disabled
                updates:
                  minecraft: false
                  mods: false
                  plugins: false
                  datapacks: false
                """;

        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, config);
    }

    /**
     * Creates a backup directory structure with timestamped backups.
     *
     * @param serverDir Server directory
     * @param backupNames List of backup names (format: yyyyMMdd-HHmmss_reason)
     * @return Path to the backups directory
     */
    public static Path createBackupsDirectory(Path serverDir, List<String> backupNames) throws IOException {
        Path backupsDir = serverDir.resolve("woflo/backups");
        Files.createDirectories(backupsDir);

        for (String name : backupNames) {
            Path backupDir = backupsDir.resolve(name);
            Files.createDirectories(backupDir);
            // Add a marker file so it's a valid backup
            Files.writeString(backupDir.resolve("current_version.txt"), "1.21.1");
        }

        return backupsDir;
    }

    /**
     * Creates a mod JAR file with specified SHA-512 hash content.
     * Useful for testing hash verification.
     *
     * @param jarPath Path to create the JAR
     * @param modId Mod ID
     * @param version Mod version
     * @param uniqueContent Unique content to ensure distinct hash
     */
    public static void createFabricModWithContent(Path jarPath, String modId, String version, String uniqueContent) throws IOException {
        String fabricModJson = """
                {
                    "schemaVersion": 1,
                    "id": "%s",
                    "version": "%s",
                    "name": "%s",
                    "environment": "*",
                    "custom": "%s"
                }
                """.formatted(modId, version, capitalize(modId), uniqueContent);

        createMinimalJar(jarPath, Map.of(
                "fabric.mod.json", fabricModJson,
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
        ));
    }
}
