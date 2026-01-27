package dev.woflo.fabric;

import dev.woflo.fabric.*;
import dev.woflo.fabric.TestTags.UnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for ModScanner.java.
 * Tests mod scanning, plugin scanning, datapack scanning, skip file handling,
 * and metadata extraction.
 * <p>
 * Uses SAME_THREAD execution because ModScanner uses a static wofloDir field
 * that would cause race conditions under parallel execution.
 */
@UnitTest
@DisplayName("ModScanner")
@Execution(ExecutionMode.SAME_THREAD)
class ModScannerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Initialize ModScanner with woflo directory
        Path wofloDir = tempDir.resolve("woflo");
        Files.createDirectories(wofloDir);
        ModScanner.init(wofloDir);
    }

    // ========================================================================
    // Mod Scanning
    // ========================================================================

    @Nested
    @DisplayName("Mod scanning")
    class ModScanning {

        @Test
        @DisplayName("scans Fabric mods and extracts IDs")
        void scansFabricModsAndExtractsIds() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("sodium-0.5.8.jar"), "sodium", "0.5.8");
            TestFixtures.createFabricMod(modsDir.resolve("lithium-0.12.1.jar"), "lithium", "0.12.1");

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            assertThat(mods).hasSize(2);
            assertThat(mods).extracting(ModScanner.Mod::id)
                    .containsExactlyInAnyOrder("sodium", "lithium");
        }

        @Test
        @DisplayName("scans Quilt mods with quilt.mod.json")
        void scansQuiltMods() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createQuiltMod(modsDir.resolve("qsl-1.0.0.jar"), "qsl", "1.0.0");

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.QUILT);

            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).id()).isEqualTo("qsl");
        }

        @Test
        @DisplayName("scans Forge mods with mods.toml")
        void scansForgeMods() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createForgeMod(modsDir.resolve("jei-1.0.0.jar"), "jei", "1.0.0");

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FORGE);

            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).id()).isEqualTo("jei");
        }

        @Test
        @DisplayName("scans NeoForge mods with neoforge.mods.toml")
        void scansNeoForgeMods() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createNeoForgeMod(modsDir.resolve("create-1.0.0.jar"), "create", "1.0.0");

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.NEOFORGE);

            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).id()).isEqualTo("create");
        }

        @Test
        @DisplayName("ignores non-JAR files")
        void ignoresNonJarFiles() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("real-mod.jar"), "realmod", "1.0");
            Files.writeString(modsDir.resolve("readme.txt"), "Not a mod");
            Files.writeString(modsDir.resolve("config.json"), "{}");

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).id()).isEqualTo("realmod");
        }

        @Test
        @DisplayName("ignores mods without valid metadata")
        void ignoresModsWithoutValidMetadata() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("valid-mod.jar"), "validmod", "1.0");
            TestFixtures.createMinimalJar(modsDir.resolve("invalid-mod.jar"), Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
            ));

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).id()).isEqualTo("validmod");
        }

        @Test
        @DisplayName("filters out ignored mod IDs")
        void filtersOutIgnoredModIds() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("sodium-0.5.8.jar"), "sodium", "0.5.8");
            TestFixtures.createFabricMod(modsDir.resolve("fabricloader.jar"), "fabricloader", "0.16.0");
            TestFixtures.createFabricMod(modsDir.resolve("java-compat.jar"), "java", "21");

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            // fabricloader and java should be filtered out
            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).id()).isEqualTo("sodium");
        }

        @Test
        @DisplayName("returns empty list for non-existent directory")
        void returnsEmptyListForNonExistentDirectory() throws IOException {
            Path modsDir = tempDir.resolve("nonexistent");

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            assertThat(mods).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for empty directory")
        void returnsEmptyListForEmptyDirectory() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            assertThat(mods).isEmpty();
        }

        @Test
        @DisplayName("includes file path in Mod record")
        void includesFilePathInModRecord() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Path modJar = modsDir.resolve("test-mod.jar");
            TestFixtures.createFabricMod(modJar, "testmod", "1.0");

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).path()).isEqualTo(modJar);
            assertThat(mods.get(0).fileName()).isEqualTo("test-mod.jar");
        }
    }

    // ========================================================================
    // Plugin Scanning
    // ========================================================================

    @Nested
    @DisplayName("Plugin scanning")
    class PluginScanning {

        @Test
        @DisplayName("scans plugins with plugin.yml")
        void scansPluginsWithPluginYml() throws IOException {
            Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins"));
            TestFixtures.createPlugin(pluginsDir.resolve("EssentialsX-2.20.jar"), "Essentials", "2.20.0");
            TestFixtures.createPlugin(pluginsDir.resolve("Vault-1.7.jar"), "Vault", "1.7.3");

            List<ModScanner.Mod> plugins = ModScanner.scanPlugins(pluginsDir);

            assertThat(plugins).hasSize(2);
            assertThat(plugins).extracting(ModScanner.Mod::id)
                    .containsExactlyInAnyOrder("Essentials", "Vault");
        }

        @Test
        @DisplayName("scans BungeeCord plugins with bungee.yml")
        void scansBungeePlugins() throws IOException {
            Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins"));
            TestFixtures.createBungeePlugin(pluginsDir.resolve("BungeePlugin-1.0.jar"), "BungeePlugin", "1.0.0");

            List<ModScanner.Mod> plugins = ModScanner.scanPlugins(pluginsDir);

            assertThat(plugins).hasSize(1);
            assertThat(plugins.get(0).id()).isEqualTo("BungeePlugin");
        }

        @Test
        @DisplayName("scans Velocity plugins with velocity-plugin.json")
        void scansVelocityPlugins() throws IOException {
            Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins"));
            TestFixtures.createVelocityPlugin(pluginsDir.resolve("VelocityPlugin-1.0.jar"), "velocityplugin", "1.0.0");

            List<ModScanner.Mod> plugins = ModScanner.scanPlugins(pluginsDir);

            assertThat(plugins).hasSize(1);
            assertThat(plugins.get(0).id()).isEqualTo("velocityplugin");
        }

        @Test
        @DisplayName("handles mixed plugin types")
        void handlesMixedPluginTypes() throws IOException {
            Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins"));
            TestFixtures.createPlugin(pluginsDir.resolve("SpigotPlugin.jar"), "SpigotPlugin", "1.0");
            TestFixtures.createBungeePlugin(pluginsDir.resolve("BungeePlugin.jar"), "BungeePlugin", "1.0");
            TestFixtures.createVelocityPlugin(pluginsDir.resolve("VelocityPlugin.jar"), "velocityplugin", "1.0");

            List<ModScanner.Mod> plugins = ModScanner.scanPlugins(pluginsDir);

            assertThat(plugins).hasSize(3);
        }

        @Test
        @DisplayName("ignores non-JAR files in plugins directory")
        void ignoresNonJarFiles() throws IOException {
            Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins"));
            TestFixtures.createPlugin(pluginsDir.resolve("RealPlugin.jar"), "RealPlugin", "1.0");
            Files.createDirectories(pluginsDir.resolve("PluginData"));
            Files.writeString(pluginsDir.resolve("config.yml"), "config: true");

            List<ModScanner.Mod> plugins = ModScanner.scanPlugins(pluginsDir);

            assertThat(plugins).hasSize(1);
        }
    }

    // ========================================================================
    // Datapack Scanning
    // ========================================================================

    @Nested
    @DisplayName("Datapack scanning")
    class DatapackScanning {

        @Test
        @DisplayName("scans datapacks with pack.mcmeta")
        void scansDatapacksWithPackMcmeta() throws IOException {
            Path datapacksDir = Files.createDirectories(tempDir.resolve("world/datapacks"));
            TestFixtures.createDatapack(datapacksDir.resolve("custom-recipes.zip"), "custom-recipes", "Custom Recipes");

            List<ModScanner.Mod> datapacks = ModScanner.scanDatapacks(datapacksDir);

            assertThat(datapacks).hasSize(1);
            assertThat(datapacks.get(0).id()).isEqualTo("custom-recipes");
        }

        @Test
        @DisplayName("extracts ID from filename when no metadata")
        void extractsIdFromFilename() throws IOException {
            Path datapacksDir = Files.createDirectories(tempDir.resolve("world/datapacks"));
            // Create a minimal ZIP without modrinth.json or proper pack ID
            try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(datapacksDir.resolve("my-datapack-1.0.zip")))) {
                zos.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));
                zos.write("""
                        {
                            "pack": {
                                "pack_format": 48,
                                "description": "Test"
                            }
                        }
                        """.getBytes());
                zos.closeEntry();
            }

            List<ModScanner.Mod> datapacks = ModScanner.scanDatapacks(datapacksDir);

            assertThat(datapacks).hasSize(1);
            // Should extract "my-datapack" from filename
            assertThat(datapacks.get(0).id()).isEqualTo("my-datapack");
        }

        @Test
        @DisplayName("ignores non-ZIP files")
        void ignoresNonZipFiles() throws IOException {
            Path datapacksDir = Files.createDirectories(tempDir.resolve("world/datapacks"));
            TestFixtures.createDatapack(datapacksDir.resolve("valid.zip"), "valid", "Valid");
            Files.writeString(datapacksDir.resolve("readme.txt"), "Not a datapack");

            List<ModScanner.Mod> datapacks = ModScanner.scanDatapacks(datapacksDir);

            assertThat(datapacks).hasSize(1);
        }
    }

    // ========================================================================
    // Skip File Handling
    // ========================================================================

    @Nested
    @DisplayName("Skip file handling")
    class SkipFileHandling {

        @Test
        @DisplayName("creates mods.txt with scanned mods")
        void createsModsTxtWithScannedMods() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("sodium-0.5.8.jar"), "sodium", "0.5.8");
            TestFixtures.createFabricMod(modsDir.resolve("lithium-0.12.1.jar"), "lithium", "0.12.1");

            ModScanner.scan(modsDir, Loader.FABRIC);

            Path modsTxt = tempDir.resolve("woflo/mods.txt");
            assertThat(modsTxt).exists();

            String content = Files.readString(modsTxt);
            assertThat(content).contains("sodium-0.5.8.jar");
            assertThat(content).contains("lithium-0.12.1.jar");
        }

        @Test
        @DisplayName("skips mods marked with # in mods.txt")
        void skipsModsMarkedWithHash() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("sodium-0.5.8.jar"), "sodium", "0.5.8");
            TestFixtures.createFabricMod(modsDir.resolve("lithium-0.12.1.jar"), "lithium", "0.12.1");

            // Create skip file marking lithium as skipped
            Path modsTxt = tempDir.resolve("woflo/mods.txt");
            Files.writeString(modsTxt, """
                    # Mod List - add # before filename to skip updates

                    # lithium-0.12.1.jar
                    sodium-0.5.8.jar
                    """);

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            // Only sodium should be in the result (not skipped)
            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).id()).isEqualTo("sodium");
        }

        @Test
        @DisplayName("preserves skip status when mods.txt is updated")
        void preservesSkipStatusWhenUpdated() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("sodium-0.5.8.jar"), "sodium", "0.5.8");

            // Create skip file
            Path modsTxt = tempDir.resolve("woflo/mods.txt");
            Files.writeString(modsTxt, "# sodium-0.5.8.jar\n");

            // Add new mod
            TestFixtures.createFabricMod(modsDir.resolve("lithium-0.12.1.jar"), "lithium", "0.12.1");

            ModScanner.scan(modsDir, Loader.FABRIC);

            // Check that sodium is still marked as skipped
            String content = Files.readString(modsTxt);
            assertThat(content).contains("# sodium-0.5.8.jar");
            assertThat(content).contains("lithium-0.12.1.jar");
            assertThat(content).doesNotContain("# lithium");
        }

        @Test
        @DisplayName("handles case-insensitive skip matching")
        void handlesCaseInsensitiveSkipMatching() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("Sodium-0.5.8.jar"), "sodium", "0.5.8");

            Path modsTxt = tempDir.resolve("woflo/mods.txt");
            Files.writeString(modsTxt, "# sodium-0.5.8.jar\n"); // lowercase in skip file

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            // Should be skipped despite case difference
            assertThat(mods).isEmpty();
        }

        @Test
        @DisplayName("creates plugins.txt for plugin scanning")
        void createsPluginsTxtForPluginScanning() throws IOException {
            Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins"));
            TestFixtures.createPlugin(pluginsDir.resolve("Essentials.jar"), "Essentials", "2.20");

            ModScanner.scanPlugins(pluginsDir);

            Path pluginsTxt = tempDir.resolve("woflo/plugins.txt");
            assertThat(pluginsTxt).exists();
            assertThat(Files.readString(pluginsTxt)).contains("Essentials.jar");
        }

        @Test
        @DisplayName("creates datapacks.txt for datapack scanning")
        void createsDatapacksTxtForDatapackScanning() throws IOException {
            Path datapacksDir = Files.createDirectories(tempDir.resolve("world/datapacks"));
            TestFixtures.createDatapack(datapacksDir.resolve("custom.zip"), "custom", "Custom");

            ModScanner.scanDatapacks(datapacksDir);

            Path datapacksTxt = tempDir.resolve("woflo/datapacks.txt");
            assertThat(datapacksTxt).exists();
            assertThat(Files.readString(datapacksTxt)).contains("custom.zip");
        }
    }

    // ========================================================================
    // World Name Detection
    // ========================================================================

    @Nested
    @DisplayName("World name detection")
    class WorldNameDetection {

        @Test
        @DisplayName("extracts world name from server.properties")
        void extractsWorldNameFromServerProperties() throws IOException {
            Files.writeString(tempDir.resolve("server.properties"), """
                    server-port=25565
                    level-name=my_custom_world
                    online-mode=true
                    """);

            String worldName = ModScanner.worldName(tempDir);

            assertThat(worldName).isEqualTo("my_custom_world");
        }

        @Test
        @DisplayName("returns 'world' as default")
        void returnsWorldAsDefault() throws IOException {
            // No server.properties

            String worldName = ModScanner.worldName(tempDir);

            assertThat(worldName).isEqualTo("world");
        }

        @Test
        @DisplayName("returns 'world' when level-name is missing")
        void returnsWorldWhenLevelNameMissing() throws IOException {
            Files.writeString(tempDir.resolve("server.properties"), """
                    server-port=25565
                    online-mode=true
                    """);

            String worldName = ModScanner.worldName(tempDir);

            assertThat(worldName).isEqualTo("world");
        }
    }

    // ========================================================================
    // Skip File Migration
    // ========================================================================

    @Nested
    @DisplayName("Skip file migration")
    class SkipFileMigration {

        @Test
        @DisplayName("migrates mods.txt from mods folder to woflo")
        void migratesModsTxtFromModsFolder() throws IOException {
            // Create old location
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Files.writeString(modsDir.resolve("mods.txt"), "# old-mod.jar\n");

            Path wofloDir = tempDir.resolve("woflo");
            Files.createDirectories(wofloDir);

            // Re-init to trigger migration
            ModScanner.init(wofloDir);

            // Old file should be moved
            assertThat(modsDir.resolve("mods.txt")).doesNotExist();
            assertThat(wofloDir.resolve("mods.txt")).exists();
            assertThat(Files.readString(wofloDir.resolve("mods.txt"))).contains("old-mod.jar");
        }

        @Test
        @DisplayName("does not overwrite existing woflo skip file")
        void doesNotOverwriteExistingWofloSkipFile() throws IOException {
            // Create both locations
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            Files.writeString(modsDir.resolve("mods.txt"), "# old-location.jar\n");

            Path wofloDir = Files.createDirectories(tempDir.resolve("woflo"));
            Files.writeString(wofloDir.resolve("mods.txt"), "# new-location.jar\n");

            ModScanner.init(wofloDir);

            // woflo version should be preserved
            assertThat(Files.readString(wofloDir.resolve("mods.txt"))).contains("new-location.jar");
            // Old version should remain (not moved since destination exists)
            assertThat(modsDir.resolve("mods.txt")).exists();
        }
    }

    // ========================================================================
    // Parallel Scanning
    // ========================================================================

    @Nested
    @DisplayName("Parallel scanning")
    class ParallelScanning {

        @Test
        @DisplayName("scans many mods efficiently using virtual threads")
        void scansManyModsEfficiently() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));

            // Create 50 mods
            for (int i = 0; i < 50; i++) {
                TestFixtures.createFabricMod(modsDir.resolve("mod-" + i + ".jar"), "mod" + i, "1.0");
            }

            long start = System.currentTimeMillis();
            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(mods).hasSize(50);
            // Should complete reasonably fast due to parallel scanning
            assertThat(elapsed).isLessThan(30000); // 30 seconds max
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles corrupted JAR files")
        void handlesCorruptedJarFiles() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("valid.jar"), "valid", "1.0");
            Files.writeString(modsDir.resolve("corrupted.jar"), "not a valid zip");

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            // Should only find the valid mod
            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).id()).isEqualTo("valid");
        }

        @Test
        @DisplayName("handles empty JAR files")
        void handlesEmptyJarFiles() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("valid.jar"), "valid", "1.0");
            Files.createFile(modsDir.resolve("empty.jar"));

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            assertThat(mods).hasSize(1);
        }

        @Test
        @DisplayName("handles mods with empty mod ID")
        void handlesModsWithEmptyModId() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("valid.jar"), "valid", "1.0");

            // Create mod with empty ID
            TestFixtures.createMinimalJar(modsDir.resolve("empty-id.jar"), Map.of(
                    "fabric.mod.json", """
                            {
                                "schemaVersion": 1,
                                "id": "",
                                "version": "1.0.0"
                            }
                            """,
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
            ));

            List<ModScanner.Mod> mods = ModScanner.scan(modsDir, Loader.FABRIC);

            // Empty ID mod should be filtered out
            assertThat(mods).hasSize(1);
            assertThat(mods.get(0).id()).isEqualTo("valid");
        }

        @Test
        @DisplayName("handles plugin with quoted name in YAML")
        void handlesPluginWithQuotedName() throws IOException {
            Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins"));

            TestFixtures.createMinimalJar(pluginsDir.resolve("quoted-plugin.jar"), Map.of(
                    "plugin.yml", """
                            name: "QuotedPlugin"
                            version: "1.0.0"
                            main: com.example.Main
                            """,
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
            ));

            List<ModScanner.Mod> plugins = ModScanner.scanPlugins(pluginsDir);

            assertThat(plugins).hasSize(1);
            assertThat(plugins.get(0).id()).isEqualTo("QuotedPlugin");
        }

        @Test
        @DisplayName("handles plugin with single-quoted name in YAML")
        void handlesPluginWithSingleQuotedName() throws IOException {
            Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins"));

            TestFixtures.createMinimalJar(pluginsDir.resolve("single-quoted.jar"), Map.of(
                    "plugin.yml", """
                            name: 'SingleQuotedPlugin'
                            version: '1.0.0'
                            main: com.example.Main
                            """,
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
            ));

            List<ModScanner.Mod> plugins = ModScanner.scanPlugins(pluginsDir);

            assertThat(plugins).hasSize(1);
            assertThat(plugins.get(0).id()).isEqualTo("SingleQuotedPlugin");
        }
    }
}
