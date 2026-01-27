package dev.woflo.fabric;

import dev.woflo.fabric.TestTags.UnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for Loader.java.
 * Consolidated from AllLoadersTest, LoaderEnumTest, and LoaderDetectionTest.
 *
 * Tests:
 * - Loader detection (all strategies and priority)
 * - Loader properties (usesMods, usesPlugins, displayName)
 * - Mod ID reading from JAR files
 * - Server JAR finding
 * - Backup items
 * - Modrinth loader mappings
 * - Cross-loader validation (parameterized)
 */
@UnitTest
@TestTags.LoaderTest
@DisplayName("Loader")
class LoaderTests {

    @TempDir
    Path tempDir;

    // ========================================================================
    // Detection - Launcher JAR (Highest Priority)
    // ========================================================================

    @Nested
    @DisplayName("Detection by launcher JAR")
    class LauncherJarDetection {

        @Test
        @DisplayName("fabric-server-launch.jar indicates FABRIC")
        void fabricServerLaunchJarIndicatesFabric() throws IOException {
            Files.createFile(tempDir.resolve("fabric-server-launch.jar"));
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.FABRIC);
        }

        @Test
        @DisplayName(".fabric marker file indicates FABRIC")
        void fabricMarkerFileIndicatesFabric() throws IOException {
            Files.createDirectories(tempDir.resolve(".fabric"));
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.FABRIC);
        }

        @Test
        @DisplayName("quilt-server-launch.jar indicates QUILT")
        void quiltServerLaunchJarIndicatesQuilt() throws IOException {
            Files.createFile(tempDir.resolve("quilt-server-launch.jar"));
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.QUILT);
        }

        @Test
        @DisplayName(".quilt marker file indicates QUILT")
        void quiltMarkerFileIndicatesQuilt() throws IOException {
            Files.createDirectories(tempDir.resolve(".quilt"));
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.QUILT);
        }

        @Test
        @DisplayName("Fabric launcher takes priority over mod metadata")
        void fabricLauncherTakesPriorityOverMods() throws IOException {
            Files.createFile(tempDir.resolve("fabric-server-launch.jar"));
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createForgeMod(modsDir.resolve("test.jar"), "test", "1.0");
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.FABRIC);
        }

        @Test
        @DisplayName("Quilt launcher takes priority over mod metadata")
        void quiltLauncherTakesPriorityOverMods() throws IOException {
            Files.createFile(tempDir.resolve("quilt-server-launch.jar"));
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("test.jar"), "test", "1.0");
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.QUILT);
        }
    }

    // ========================================================================
    // Detection - Mod Metadata
    // ========================================================================

    @Nested
    @DisplayName("Detection by mod metadata")
    class ModMetadataDetection {

        @Test
        @DisplayName("fabric.mod.json in mods folder indicates FABRIC")
        void fabricModJsonIndicatesFabric() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("test-mod.jar"), "test-mod", "1.0.0");
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.FABRIC);
        }

        @Test
        @DisplayName("quilt.mod.json in mods folder indicates QUILT")
        void quiltModJsonIndicatesQuilt() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createQuiltMod(modsDir.resolve("test-mod.jar"), "test-mod", "1.0.0");
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.QUILT);
        }

        @Test
        @DisplayName("mods.toml in mods folder indicates FORGE")
        void modsTomlIndicatesForge() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createForgeMod(modsDir.resolve("test-mod.jar"), "test-mod", "1.0.0");
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.FORGE);
        }

        @Test
        @DisplayName("neoforge.mods.toml in mods folder indicates NEOFORGE")
        void neoforgeModsTomlIndicatesNeoforge() throws IOException {
            Path modsDir = Files.createDirectories(tempDir.resolve("mods"));
            TestFixtures.createNeoForgeMod(modsDir.resolve("test-mod.jar"), "test-mod", "1.0.0");
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.NEOFORGE);
        }
    }

    // ========================================================================
    // Detection - JAR Name Pattern
    // ========================================================================

    @Nested
    @DisplayName("Detection by JAR name")
    class JarNameDetection {

        @Test
        @DisplayName("JAR with 'forge' in name indicates FORGE")
        void jarWithForgeInNameIndicatesForge() throws IOException {
            TestFixtures.createMinimalJar(tempDir.resolve("forge-1.21.1-52.0.jar"), Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
            ));
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.FORGE);
        }

        @Test
        @DisplayName("JAR with 'Forge' (capitalized) indicates FORGE")
        void jarWithCapitalizedForgeIndicatesForge() throws IOException {
            TestFixtures.createMinimalJar(tempDir.resolve("MinecraftForge-1.21.1.jar"), Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
            ));
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.FORGE);
        }
    }

    // ========================================================================
    // Detection - Paperclip/Plugin Servers
    // ========================================================================

    @Nested
    @DisplayName("Detection by paperclip manifest")
    class PaperclipDetection {

        @Test
        @DisplayName("paperclip Main-Class indicates PAPER")
        void paperclipMainClassIndicatesPaper() throws IOException {
            TestFixtures.createPaperJar(tempDir.resolve("paper.jar"), "paper");
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.PAPER);
        }

        @Test
        @DisplayName("purpur.jar with paperclip indicates PURPUR")
        void purpurJarWithPaperclipIndicatesPurpur() throws IOException {
            TestFixtures.createPaperJar(tempDir.resolve("purpur.jar"), "purpur");
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.PURPUR);
        }

        @Test
        @DisplayName("folia.jar with paperclip indicates FOLIA")
        void foliaJarWithPaperclipIndicatesFolia() throws IOException {
            TestFixtures.createPaperJar(tempDir.resolve("folia.jar"), "folia");
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.FOLIA);
        }

        @Test
        @DisplayName("plugins folder without mods indicates PAPER")
        void pluginsFolderWithoutModsIndicatesPaper() throws IOException {
            Files.createDirectories(tempDir.resolve("plugins"));
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.PAPER);
        }
    }

    // ========================================================================
    // Detection - Vanilla Fallback
    // ========================================================================

    @Nested
    @DisplayName("Detection - Vanilla and defaults")
    class VanillaDetection {

        @Test
        @DisplayName("server.jar alone indicates VANILLA")
        void serverJarAloneIndicatesVanilla() throws IOException {
            TestFixtures.createMinimalJar(tempDir.resolve("server.jar"), Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: net.minecraft.server.Main\n"
            ));
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.VANILLA);
        }

        @Test
        @DisplayName("server.jar with empty mods folder indicates VANILLA")
        void serverJarWithEmptyModsIndicatesVanilla() throws IOException {
            TestFixtures.createMinimalJar(tempDir.resolve("server.jar"), Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: net.minecraft.server.Main\n"
            ));
            Files.createDirectories(tempDir.resolve("mods"));
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.VANILLA);
        }

        @Test
        @DisplayName("empty directory defaults to FABRIC")
        void emptyDirectoryDefaultsToFabric() {
            assertThat(Loader.detect(tempDir)).isEqualTo(Loader.FABRIC);
        }

        @Test
        @DisplayName("non-existent directory defaults to FABRIC")
        void nonExistentDirectoryDefaultsToFabric() {
            Path nonExistent = tempDir.resolve("does-not-exist");
            assertThat(Loader.detect(nonExistent)).isEqualTo(Loader.FABRIC);
        }
    }

    // ========================================================================
    // Detection - Full Server Fixtures (Parameterized)
    // ========================================================================

    @Nested
    @DisplayName("Detection from full server fixtures")
    class FullServerDetection {

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("detects loader from full server setup")
        void detectsLoaderFromFullSetup(Loader expectedLoader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, expectedLoader);
            assertThat(Loader.detect(serverDir)).isEqualTo(expectedLoader);
        }

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("creates server fixture for each loader")
        void createsServerFixtureForEachLoader(Loader loader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, loader);
            assertThat(serverDir).exists().isDirectory();
            assertThat(serverDir.resolve("woflo")).exists();
            assertThat(serverDir.resolve("current_version.txt")).exists();
        }
    }

    // ========================================================================
    // Loader Properties
    // ========================================================================

    @Nested
    @DisplayName("Loader properties")
    class Properties {

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("each loader has a display name")
        void eachLoaderHasDisplayName(Loader loader) {
            String displayName = loader.displayName();
            assertThat(displayName).isNotBlank();
            assertThat(Character.isUpperCase(displayName.charAt(0))).isTrue();
            assertThat(displayName).doesNotContain("_");
        }

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("each loader has a server JAR name")
        void eachLoaderHasServerJarName(Loader loader) {
            String jarName = loader.serverJar();
            assertThat(jarName).isNotBlank();
            assertThat(jarName).matches(".*\\.(jar|bat|sh)");
        }

        @Test
        @DisplayName("serverJar() returns expected names")
        void serverJarReturnsExpectedNames() {
            assertThat(Loader.FABRIC.serverJar()).isEqualTo("fabric-server-launch.jar");
            assertThat(Loader.QUILT.serverJar()).isEqualTo("quilt-server-launch.jar");
            assertThat(Loader.FORGE.serverJar()).isEqualTo("forge-server.jar");
            assertThat(Loader.NEOFORGE.serverJar()).isEqualTo("neoforge-server.jar");
            assertThat(Loader.PAPER.serverJar()).isEqualTo("paper.jar");
            assertThat(Loader.PURPUR.serverJar()).isEqualTo("purpur.jar");
            assertThat(Loader.FOLIA.serverJar()).isEqualTo("folia.jar");
            assertThat(Loader.VANILLA.serverJar()).isEqualTo("server.jar");
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"FABRIC", "QUILT", "FORGE", "NEOFORGE"})
        @DisplayName("mod loaders use mods")
        void modLoadersUseMods(Loader loader) {
            assertThat(loader.usesMods()).isTrue();
            assertThat(loader.usesPlugins()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"PAPER", "PURPUR", "FOLIA"})
        @DisplayName("plugin loaders use plugins")
        void pluginLoadersUsePlugins(Loader loader) {
            assertThat(loader.usesPlugins()).isTrue();
            assertThat(loader.usesMods()).isFalse();
        }

        @Test
        @DisplayName("Vanilla uses neither mods nor plugins")
        void vanillaUsesNeither() {
            assertThat(Loader.VANILLA.usesMods()).isFalse();
            assertThat(Loader.VANILLA.usesPlugins()).isFalse();
        }
    }

    // ========================================================================
    // Mod ID Reading
    // ========================================================================

    @Nested
    @DisplayName("Mod ID reading")
    class ModIdReading {

        @Test
        @DisplayName("reads mod ID from Fabric mod")
        void readsModIdFromFabricMod() throws IOException {
            Path modJar = tempDir.resolve("test-mod.jar");
            TestFixtures.createFabricMod(modJar, "my-mod", "1.0.0");
            assertThat(Loader.FABRIC.readModId(modJar)).isEqualTo("my-mod");
        }

        @Test
        @DisplayName("reads mod ID from Quilt mod")
        void readsModIdFromQuiltMod() throws IOException {
            Path modJar = tempDir.resolve("test-mod.jar");
            TestFixtures.createQuiltMod(modJar, "my-mod", "1.0.0");
            assertThat(Loader.QUILT.readModId(modJar)).isEqualTo("my-mod");
        }

        @Test
        @DisplayName("reads mod ID from Forge mod")
        void readsModIdFromForgeMod() throws IOException {
            Path modJar = tempDir.resolve("test-mod.jar");
            TestFixtures.createForgeMod(modJar, "my-mod", "1.0.0");
            assertThat(Loader.FORGE.readModId(modJar)).isEqualTo("my-mod");
        }

        @Test
        @DisplayName("reads mod ID from NeoForge mod")
        void readsModIdFromNeoForgeMod() throws IOException {
            Path modJar = tempDir.resolve("test-mod.jar");
            TestFixtures.createNeoForgeMod(modJar, "my-mod", "1.0.0");
            assertThat(Loader.NEOFORGE.readModId(modJar)).isEqualTo("my-mod");
        }

        @Test
        @DisplayName("Quilt falls back to fabric.mod.json")
        void quiltFallsBackToFabricMod() throws IOException {
            Path modJar = tempDir.resolve("test-mod.jar");
            TestFixtures.createFabricMod(modJar, "fabric-mod", "1.0.0");
            assertThat(Loader.QUILT.readModId(modJar)).isEqualTo("fabric-mod");
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"PAPER", "PURPUR", "FOLIA", "VANILLA"})
        @DisplayName("returns null for non-mod loaders")
        void returnsNullForNonModLoaders(Loader loader) throws IOException {
            Path modJar = tempDir.resolve("test-mod.jar");
            TestFixtures.createFabricMod(modJar, "my-mod", "1.0.0");
            assertThat(loader.readModId(modJar)).isNull();
        }

        @Test
        @DisplayName("returns null for invalid JAR")
        void returnsNullForInvalidJar() throws IOException {
            Path invalidJar = tempDir.resolve("invalid.jar");
            Files.writeString(invalidJar, "not a valid jar");
            assertThat(Loader.FABRIC.readModId(invalidJar)).isNull();
        }

        @Test
        @DisplayName("returns null for JAR without mod metadata")
        void returnsNullForJarWithoutMetadata() throws IOException {
            Path modJar = tempDir.resolve("no-metadata.jar");
            TestFixtures.createMinimalJar(modJar, Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"
            ));
            assertThat(Loader.FABRIC.readModId(modJar)).isNull();
        }

        @Test
        @DisplayName("returns null for non-existent file")
        void returnsNullForNonExistentFile() {
            Path nonExistent = tempDir.resolve("does-not-exist.jar");
            assertThat(Loader.FABRIC.readModId(nonExistent)).isNull();
        }
    }

    // ========================================================================
    // Server JAR Finding
    // ========================================================================

    @Nested
    @DisplayName("Server JAR finding")
    class ServerJarFinding {

        @Test
        @DisplayName("finds expected JAR when present")
        void findsExpectedJarWhenPresent() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            assertThat(Loader.FABRIC.findServerJar(serverDir)).isEqualTo("fabric-server-launch.jar");
        }

        @Test
        @DisplayName("finds JAR by name pattern")
        void findsJarByNamePattern() throws IOException {
            TestFixtures.createMinimalJar(tempDir.resolve("paper-1.21.1-130.jar"), Map.of(
                    "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: io.papermc.paperclip.Main\n"
            ));
            assertThat(Loader.PAPER.findServerJar(tempDir)).isEqualTo("paper-1.21.1-130.jar");
        }

        @Test
        @DisplayName("excludes installer JARs from search")
        void excludesInstallerJarsFromSearch() throws IOException {
            TestFixtures.createMinimalJar(tempDir.resolve("fabric-installer.jar"), Map.of());
            TestFixtures.createMinimalJar(tempDir.resolve("fabric-server-launch.jar"), Map.of());
            assertThat(Loader.FABRIC.findServerJar(tempDir)).isEqualTo("fabric-server-launch.jar");
        }

        @Test
        @DisplayName("excludes maintainer JAR from search")
        void excludesMaintainerJarFromSearch() throws IOException {
            TestFixtures.createMinimalJar(tempDir.resolve("server maintainer by woflo.jar"), Map.of());
            TestFixtures.createMinimalJar(tempDir.resolve("paper.jar"), Map.of());
            String found = Loader.PAPER.findServerJar(tempDir);
            assertThat(found).doesNotContain("woflo").doesNotContain("maintainer");
        }

        @Test
        @DisplayName("Forge/NeoForge uses run scripts when present")
        void forgeUsesRunScriptsWhenPresent() throws IOException {
            Files.writeString(tempDir.resolve("run.bat"), "@echo off");
            Files.writeString(tempDir.resolve("run.sh"), "#!/bin/bash");
            String found = Loader.FORGE.findServerJar(tempDir);
            assertThat(found).isIn("run.bat", "run.sh");
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"FABRIC", "QUILT", "PAPER", "PURPUR", "FOLIA", "VANILLA"})
        @DisplayName("finds server JAR in full server setup")
        void findsServerJarInFullSetup(Loader loader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, loader);
            String jarName = loader.findServerJar(serverDir);
            assertThat(jarName).isNotBlank();
        }
    }

    // ========================================================================
    // Backup Items
    // ========================================================================

    @Nested
    @DisplayName("Backup items")
    class BackupItems {

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("each loader has backup items including current_version.txt")
        void eachLoaderHasBackupItems(Loader loader) {
            String[] items = loader.backupItems();
            assertThat(items).isNotEmpty();
            assertThat(items).contains("current_version.txt");
        }

        @Test
        @DisplayName("Fabric backup items include mods and launcher")
        void fabricBackupItems() {
            assertThat(Loader.FABRIC.backupItems())
                    .contains("mods", "versions", "libraries", "fabric-server-launch.jar", "current_version.txt");
        }

        @Test
        @DisplayName("Quilt backup items include mods and launcher")
        void quiltBackupItems() {
            assertThat(Loader.QUILT.backupItems())
                    .contains("mods", "versions", "libraries", "quilt-server-launch.jar", "current_version.txt");
        }

        @Test
        @DisplayName("Forge backup items include run scripts")
        void forgeBackupItems() {
            assertThat(Loader.FORGE.backupItems())
                    .contains("mods", "libraries", "run.bat", "run.sh", "current_version.txt");
        }

        @Test
        @DisplayName("NeoForge backup items include run scripts")
        void neoforgeBackupItems() {
            assertThat(Loader.NEOFORGE.backupItems())
                    .contains("mods", "libraries", "run.bat", "run.sh", "current_version.txt");
        }

        @Test
        @DisplayName("Paper backup items include plugins")
        void paperBackupItems() {
            assertThat(Loader.PAPER.backupItems()).contains("plugins", "paper.jar", "current_version.txt");
        }

        @Test
        @DisplayName("Vanilla backup items include server.jar")
        void vanillaBackupItems() {
            assertThat(Loader.VANILLA.backupItems()).contains("server.jar", "current_version.txt");
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"FABRIC", "QUILT", "FORGE", "NEOFORGE"})
        @DisplayName("mod loaders backup mods folder")
        void modLoadersBackupModsFolder(Loader loader) {
            assertThat(loader.backupItems()).contains("mods");
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"PAPER", "PURPUR", "FOLIA"})
        @DisplayName("plugin loaders backup plugins folder")
        void pluginLoadersBackupPluginsFolder(Loader loader) {
            assertThat(loader.backupItems()).contains("plugins");
        }
    }

    // ========================================================================
    // Ignored Mod IDs
    // ========================================================================

    @Nested
    @DisplayName("Ignored mod IDs")
    class IgnoredModIds {

        @Test
        @DisplayName("Fabric ignores core mod IDs")
        void fabricIgnoresCoreIds() {
            assertThat(Loader.FABRIC.ignoredModIds())
                    .contains("java", "minecraft", "fabricloader", "mixinextras", "fabric-api");
        }

        @Test
        @DisplayName("Quilt ignores core mod IDs")
        void quiltIgnoresCoreIds() {
            assertThat(Loader.QUILT.ignoredModIds())
                    .contains("java", "minecraft", "quilt_loader", "quilted_fabric_api");
        }

        @Test
        @DisplayName("Forge ignores core mod IDs")
        void forgeIgnoresCoreIds() {
            assertThat(Loader.FORGE.ignoredModIds()).contains("minecraft", "forge");
        }

        @Test
        @DisplayName("NeoForge ignores core mod IDs")
        void neoforgeIgnoresCoreIds() {
            assertThat(Loader.NEOFORGE.ignoredModIds()).contains("minecraft", "neoforge");
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"PAPER", "PURPUR", "FOLIA", "VANILLA"})
        @DisplayName("non-mod loaders have no ignored IDs")
        void nonModLoadersHaveNoIgnoredIds(Loader loader) {
            assertThat(loader.ignoredModIds()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"FABRIC", "QUILT", "FORGE", "NEOFORGE"})
        @DisplayName("mod loaders always ignore 'minecraft'")
        void modLoadersAlwaysIgnoreMinecraft(Loader loader) {
            assertThat(loader.ignoredModIds()).contains("minecraft");
        }
    }

    // ========================================================================
    // Modrinth Loaders
    // ========================================================================

    @Nested
    @DisplayName("Modrinth loaders")
    class ModrinthLoaders {

        @Test
        @DisplayName("Fabric returns fabric and quilt loaders")
        void fabricReturnsCorrectLoaders() {
            assertThat(Loader.FABRIC.modrinthLoaders()).containsExactly("fabric", "quilt");
        }

        @Test
        @DisplayName("Quilt returns quilt and fabric loaders")
        void quiltReturnsCorrectLoaders() {
            assertThat(Loader.QUILT.modrinthLoaders()).containsExactly("quilt", "fabric");
        }

        @Test
        @DisplayName("Forge returns forge and neoforge loaders")
        void forgeReturnsCorrectLoaders() {
            assertThat(Loader.FORGE.modrinthLoaders()).containsExactly("forge", "neoforge");
        }

        @Test
        @DisplayName("NeoForge returns neoforge and forge loaders")
        void neoforgeReturnsCorrectLoaders() {
            assertThat(Loader.NEOFORGE.modrinthLoaders()).containsExactly("neoforge", "forge");
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"PAPER", "PURPUR", "FOLIA"})
        @DisplayName("plugin loaders return paper, spigot, bukkit")
        void pluginLoadersReturnCorrectList(Loader loader) {
            assertThat(loader.modrinthLoaders()).containsExactly("paper", "spigot", "bukkit");
        }

        @Test
        @DisplayName("Vanilla returns empty list")
        void vanillaReturnsEmptyList() {
            assertThat(Loader.VANILLA.modrinthLoaders()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("each loader has modrinth loaders defined or is Vanilla")
        void eachLoaderHasModrinthLoadersOrIsVanilla(Loader loader) {
            List<String> loaders = loader.modrinthLoaders();
            assertThat(loaders).isNotNull();
            if (loader != Loader.VANILLA) {
                assertThat(loaders).isNotEmpty();
            }
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"FABRIC", "QUILT"})
        @DisplayName("Fabric/Quilt are cross-compatible on Modrinth")
        void fabricQuiltCrossCompatible(Loader loader) {
            assertThat(loader.modrinthLoaders()).contains("fabric", "quilt");
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"FORGE", "NEOFORGE"})
        @DisplayName("Forge/NeoForge are cross-compatible on Modrinth")
        void forgeNeoForgeCrossCompatible(Loader loader) {
            assertThat(loader.modrinthLoaders()).contains("forge", "neoforge");
        }
    }

    // ========================================================================
    // Content Directories
    // ========================================================================

    @Nested
    @DisplayName("Content directories")
    class ContentDirectories {

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"FABRIC", "QUILT", "FORGE", "NEOFORGE"})
        @DisplayName("mod loaders have mods directory")
        void modLoadersHaveModsDirectory(Loader loader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, loader);
            assertThat(serverDir.resolve("mods")).exists();
        }

        @ParameterizedTest
        @EnumSource(value = Loader.class, names = {"PAPER", "PURPUR", "FOLIA"})
        @DisplayName("plugin loaders have plugins directory")
        void pluginLoadersHavePluginsDirectory(Loader loader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, loader);
            assertThat(serverDir.resolve("plugins")).exists();
        }
    }

    // ========================================================================
    // Backup Operations (Cross-Loader Validation)
    // ========================================================================

    @Nested
    @DisplayName("Cross-loader backup operations")
    class CrossLoaderBackup {

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("creates backup for each loader")
        void createsBackupForEachLoader(Loader loader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, loader);
            Path backup = Backup.createSilent(serverDir, loader, "test");
            assertThat(backup).isNotNull().exists();
            assertThat(backup.resolve("current_version.txt")).exists();
        }

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("restores backup for each loader")
        void restoresBackupForEachLoader(Loader loader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, loader);
            Path backup = Backup.createSilent(serverDir, loader, "test");
            Files.writeString(serverDir.resolve("current_version.txt"), "modified");
            MockConsole console = new MockConsole();
            boolean result = Backup.restore(backup, serverDir, loader, console);
            assertThat(result).isTrue();
            assertThat(Files.readString(serverDir.resolve("current_version.txt"))).isEqualTo("1.21.1");
        }
    }

    // ========================================================================
    // Config Loading (Cross-Loader Validation)
    // ========================================================================

    @Nested
    @DisplayName("Cross-loader config loading")
    class CrossLoaderConfig {

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("config loads for each loader")
        void configLoadsForEachLoader(Loader loader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, loader);
            Config config = Config.load(serverDir);
            assertThat(config).isNotNull();
            assertThat(serverDir.resolve("woflo/config.yml")).exists();
        }

        @ParameterizedTest
        @EnumSource(Loader.class)
        @DisplayName("complete server setup works for each loader")
        void completeServerSetupWorks(Loader loader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, loader);
            TestFixtures.setupCompleteServer(serverDir, loader);

            assertThat(serverDir.resolve("woflo/config.yml")).exists();
            assertThat(serverDir.resolve("eula.txt")).exists();
            assertThat(serverDir.resolve("server.properties")).exists();
            assertThat(serverDir.resolve("current_version.txt")).exists();
            assertThat(Loader.detect(serverDir)).isEqualTo(loader);
            assertThat(Config.load(serverDir)).isNotNull();
        }
    }

    // ========================================================================
    // NeoForge Prefix Calculation
    // ========================================================================

    @Nested
    @DisplayName("NeoForge prefix calculation")
    class NeoforgePrefixCalculation {

        @Test
        @DisplayName("1.21.1 maps to 21.1. prefix")
        void mc1211MapsTo211Prefix() {
            String[] parts = "1.21.1".split("\\.");
            String prefix = (parts.length >= 2 ? parts[1] : "21") + "." +
                    (parts.length >= 3 ? parts[2] : "0") + ".";
            assertThat(prefix).isEqualTo("21.1.");
        }

        @Test
        @DisplayName("1.21 maps to 21.0. prefix")
        void mc121MapsTo210Prefix() {
            String[] parts = "1.21".split("\\.");
            String prefix = (parts.length >= 2 ? parts[1] : "21") + "." +
                    (parts.length >= 3 ? parts[2] : "0") + ".";
            assertThat(prefix).isEqualTo("21.0.");
        }

        @Test
        @DisplayName("1.20.6 maps to 20.6. prefix")
        void mc1206MapsTo206Prefix() {
            String[] parts = "1.20.6".split("\\.");
            String prefix = (parts.length >= 2 ? parts[1] : "21") + "." +
                    (parts.length >= 3 ? parts[2] : "0") + ".";
            assertThat(prefix).isEqualTo("20.6.");
        }
    }

    // ========================================================================
    // Version Comparison (Utility)
    // ========================================================================

    @Nested
    @DisplayName("Version comparison")
    class VersionComparison {

        @ParameterizedTest
        @CsvSource({
                "1.20.6, 1.21.1, -1",
                "1.21.1, 1.20.6, 1",
                "1.21.1, 1.21.1, 0",
                "1.20.10, 1.20.2, 1",
                "1.20, 1.20.1, -1"
        })
        @DisplayName("version comparison works correctly")
        void versionComparisonWorksCorrectly(String v1, String v2, int expectedSign) {
            int result = compareVersions(v1, v2);
            if (expectedSign < 0) {
                assertThat(result).isLessThan(0);
            } else if (expectedSign > 0) {
                assertThat(result).isGreaterThan(0);
            } else {
                assertThat(result).isEqualTo(0);
            }
        }

        private static int compareVersions(String a, String b) {
            String[] pa = a.split("\\."), pb = b.split("\\.");
            for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
                int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
                int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
                if (va != vb) return va - vb;
            }
            return 0;
        }
    }
}
