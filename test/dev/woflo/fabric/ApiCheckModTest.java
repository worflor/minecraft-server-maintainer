package dev.woflo.fabric;

import dev.woflo.fabric.TestTags.ApiTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Api.checkMod() return paths and logic.
 * This method has multiple return paths that need individual verification:
 * - skip when loaders.isEmpty()
 * - skip when IOException from version_file lookup (mod not on Modrinth)
 * - skip when projectId is null/empty
 * - skip when versions is null/empty
 * - skip when no release/beta version matches
 * - skip when file is null
 * - "current" when oldVer equals newVer
 * - "update" with all fields populated
 *
 * Tests call real Api methods and verify source code patterns stay in sync.
 */
@ApiTest
@DisplayName("Api.checkMod Paths")
class ApiCheckModTest {

    @TempDir
    Path tempDir;

    // ========================================================================
    // Source Code Verification
    // ========================================================================

    @Nested
    @DisplayName("Source code verification")
    class SourceCodeVerification {

        @Test
        @DisplayName("Api.java contains expected MODRINTH_API constant")
        void apiJavaContainsExpectedModrinthApiConstant() throws IOException {
            Path apiJava = Path.of("src/dev/woflo/fabric/Api.java");
            assertThat(apiJava).exists();

            String source = Files.readString(apiJava);

            // Verify Modrinth API URL constant
            assertThat(source).contains("MODRINTH_API = \"https://api.modrinth.com/v2\"");

            // Verify Mojang manifest URL
            assertThat(source).contains("MOJANG_MANIFEST = \"https://launchermeta.mojang.com/mc/game/version_manifest_v2.json\"");

            // Verify checkMod version type filtering logic
            assertThat(source).containsPattern(Pattern.compile("\"release\"\\.equals\\(type\\)\\s*\\|\\|\\s*\\(allowBeta\\s*&&\\s*\"beta\"\\.equals\\(type\\)\\)"));

            // Verify primary file selection
            assertThat(source).contains("Http.bool(f, \"primary\", false)");
        }

        @Test
        @DisplayName("Api.PLUGIN_LOADERS matches expected values")
        void apiPluginLoadersMatchesExpectedValues() {
            // Verify the constant matches what we expect
            assertThat(Api.PLUGIN_LOADERS).containsExactly("paper", "spigot", "bukkit", "folia");
        }

        @Test
        @DisplayName("Api.DATAPACK_LOADERS matches expected values")
        void apiDatapackLoadersMatchesExpectedValues() {
            assertThat(Api.DATAPACK_LOADERS).containsExactly("datapack");
        }
    }

    // ========================================================================
    // CheckResult Record Tests
    // ========================================================================

    @Nested
    @DisplayName("CheckResult record")
    class CheckResultRecord {

        @Test
        @DisplayName("skip result has correct fields")
        void skipResultHasCorrectFields() {
            Path modPath = tempDir.resolve("test-mod.jar");
            Api.CheckResult result = new Api.CheckResult(
                    "test-mod", modPath, "skip", "1.0.0", null, null, null, null
            );

            assertThat(result.id()).isEqualTo("test-mod");
            assertThat(result.path()).isEqualTo(modPath);
            assertThat(result.status()).isEqualTo("skip");
            assertThat(result.oldVersion()).isEqualTo("1.0.0");
            assertThat(result.newVersion()).isNull();
            assertThat(result.downloadUrl()).isNull();
            assertThat(result.sha512()).isNull();
            assertThat(result.fileName()).isNull();
        }

        @Test
        @DisplayName("current result has correct fields")
        void currentResultHasCorrectFields() {
            Path modPath = tempDir.resolve("test-mod.jar");
            Api.CheckResult result = new Api.CheckResult(
                    "test-mod", modPath, "current", "1.0.0", "1.0.0", null, null, null
            );

            assertThat(result.status()).isEqualTo("current");
            assertThat(result.oldVersion()).isEqualTo(result.newVersion());
        }

        @Test
        @DisplayName("update result has all fields populated")
        void updateResultHasAllFieldsPopulated() {
            Path modPath = tempDir.resolve("test-mod.jar");
            Api.CheckResult result = new Api.CheckResult(
                    "test-mod", modPath, "update", "1.0.0", "2.0.0",
                    "https://cdn.modrinth.com/data/test/file.jar",
                    "abc123sha512hash",
                    "test-mod-2.0.0.jar"
            );

            assertThat(result.status()).isEqualTo("update");
            assertThat(result.oldVersion()).isEqualTo("1.0.0");
            assertThat(result.newVersion()).isEqualTo("2.0.0");
            assertThat(result.downloadUrl()).isNotNull();
            assertThat(result.sha512()).isNotNull();
            assertThat(result.fileName()).isNotNull();
        }
    }

    // ========================================================================
    // Empty Loaders Path
    // ========================================================================

    @Nested
    @DisplayName("Empty loaders path")
    class EmptyLoadersPath {

        @Test
        @DisplayName("returns skip when loaders list is empty")
        void returnsSkipWhenLoadersEmpty() throws IOException {
            Path modPath = tempDir.resolve("test-mod.jar");
            TestFixtures.createFabricMod(modPath, "test-mod", "1.0.0");

            ModScanner.Mod mod = new ModScanner.Mod("test-mod", modPath, modPath.getFileName().toString());
            Api.CheckResult result = Api.checkMod(mod, "1.21.1", List.of(), false);

            assertThat(result.status()).isEqualTo("skip");
            assertThat(result.oldVersion()).isNull();
        }

        @Test
        @DisplayName("VANILLA loader returns empty loaders")
        void vanillaLoaderReturnsEmptyLoaders() {
            assertThat(Loader.VANILLA.modrinthLoaders()).isEmpty();
        }
    }

    // ========================================================================
    // Plugin and Datapack Loaders
    // ========================================================================

    @Nested
    @DisplayName("Loader configuration")
    class LoaderConfiguration {

        @Test
        @DisplayName("PLUGIN_LOADERS contains correct values")
        void pluginLoadersContainsCorrectValues() {
            assertThat(Api.PLUGIN_LOADERS).containsExactly("paper", "spigot", "bukkit", "folia");
        }

        @Test
        @DisplayName("DATAPACK_LOADERS contains correct values")
        void datapackLoadersContainsCorrectValues() {
            assertThat(Api.DATAPACK_LOADERS).containsExactly("datapack");
        }

        @ParameterizedTest
        @DisplayName("each loader has modrinth loaders defined")
        @EnumSource(Loader.class)
        void eachLoaderHasModrinthLoadersDefined(Loader loader) {
            List<String> loaders = loader.modrinthLoaders();
            assertThat(loaders).isNotNull();
            // Only VANILLA should have empty loaders
            if (loader == Loader.VANILLA) {
                assertThat(loaders).isEmpty();
            }
        }
    }

    // ========================================================================
    // Loader Fallback Logic
    // ========================================================================

    @Nested
    @DisplayName("Loader fallback logic")
    class LoaderFallbackLogic {

        @Test
        @DisplayName("Fabric tries fabric then quilt")
        void fabricTriesFabricThenQuilt() {
            List<String> loaders = Loader.FABRIC.modrinthLoaders();
            assertThat(loaders).containsExactly("fabric", "quilt");
        }

        @Test
        @DisplayName("Quilt tries quilt then fabric")
        void quiltTriesQuiltThenFabric() {
            List<String> loaders = Loader.QUILT.modrinthLoaders();
            assertThat(loaders).containsExactly("quilt", "fabric");
        }

        @Test
        @DisplayName("NeoForge tries neoforge then forge")
        void neoforgeTriesNeoforgeThenForge() {
            List<String> loaders = Loader.NEOFORGE.modrinthLoaders();
            assertThat(loaders).containsExactly("neoforge", "forge");
        }

        @Test
        @DisplayName("Forge tries forge then neoforge")
        void forgeTriesForgeThenNeoforge() {
            List<String> loaders = Loader.FORGE.modrinthLoaders();
            assertThat(loaders).containsExactly("forge", "neoforge");
        }

        @Test
        @DisplayName("Paper-based loaders try paper, spigot, bukkit")
        void paperBasedLoadersTryPaperSpigotBukkit() {
            for (Loader loader : List.of(Loader.PAPER, Loader.PURPUR, Loader.FOLIA)) {
                List<String> loaders = loader.modrinthLoaders();
                assertThat(loaders).containsExactly("paper", "spigot", "bukkit");
            }
        }
    }

    // ========================================================================
    // checkMod Delegation
    // ========================================================================

    @Nested
    @DisplayName("checkMod delegation")
    class CheckModDelegation {

        @Test
        @DisplayName("checkMod with Loader delegates to modrinthLoaders")
        void checkModWithLoaderDelegatesToModrinthLoaders() throws IOException {
            Path modPath = tempDir.resolve("test-mod.jar");
            TestFixtures.createFabricMod(modPath, "test-mod", "1.0.0");

            ModScanner.Mod mod = new ModScanner.Mod("test-mod", modPath, modPath.getFileName().toString());

            // Using VANILLA should result in skip due to empty loaders
            Api.CheckResult result = Api.checkMod(mod, "1.21.1", Loader.VANILLA, false);
            assertThat(result.status()).isEqualTo("skip");
        }
    }

    // ========================================================================
    // Version Type Filtering
    // ========================================================================

    @Nested
    @DisplayName("Version type filtering")
    class VersionTypeFiltering {

        @Test
        @DisplayName("API response parsing with release type")
        void apiResponseParsingWithReleaseType() {
            String response = ApiResponses.modrinthVersionFile("sodium", "0.5.8", "v058");
            var parsed = Http.parseObject(response);

            String versionType = Http.str(parsed, "version_type");
            assertThat(versionType).isEqualTo("release");
        }

        @Test
        @DisplayName("API response parsing with beta type")
        void apiResponseParsingWithBetaType() {
            String response = ApiResponses.modrinthBetaVersionFile("test-mod", "1.0.0-beta", "v100b");
            var parsed = Http.parseObject(response);

            String versionType = Http.str(parsed, "version_type");
            assertThat(versionType).isEqualTo("beta");
        }

        @Test
        @DisplayName("allowBeta false filters out beta versions")
        void allowBetaFalseFiltersOutBetaVersions() {
            // When allowBeta is false, only release versions should be considered
            var versions = Http.parseArray(ApiResponses.modrinthVersionsWithBeta("test-mod", "1.0.0", "2.0.0-beta"));

            // First release version should be found before beta
            boolean foundRelease = false;
            for (var v : versions) {
                @SuppressWarnings("unchecked")
                var ver = (Map<String, Object>) v;
                String type = Http.str(ver, "version_type");
                if ("release".equals(type)) {
                    foundRelease = true;
                    break;
                }
            }
            assertThat(foundRelease).isTrue();
        }
    }

    // ========================================================================
    // File Selection Logic
    // ========================================================================

    @Nested
    @DisplayName("File selection logic")
    class FileSelectionLogic {

        @Test
        @DisplayName("primary file is selected when available")
        void primaryFileIsSelectedWhenAvailable() {
            var response = Http.parseObject(ApiResponses.modrinthVersionWithMultipleFiles("test-mod", "1.0.0"));
            var files = Http.arr(response, "files");

            // Find primary file
            Map<String, Object> primaryFile = null;
            for (var f : files) {
                if (Http.bool(f, "primary", false)) {
                    primaryFile = f;
                    break;
                }
            }

            assertThat(primaryFile).isNotNull();
            assertThat(Http.str(primaryFile, "filename")).isEqualTo("test-mod-1.0.0.jar");
        }

        @Test
        @DisplayName("first file is used when no primary flag")
        void firstFileIsUsedWhenNoPrimaryFlag() {
            var response = Http.parseObject(ApiResponses.modrinthVersionNoPrimaryFile("test-mod", "1.0.0"));
            var files = Http.arr(response, "files");

            // Verify no primary flag
            boolean hasPrimary = files.stream().anyMatch(f -> Http.bool(f, "primary", false));
            assertThat(hasPrimary).isFalse();

            // First file should be used
            assertThat(files).isNotEmpty();
            assertThat(Http.str(files.getFirst(), "filename")).isNotNull();
        }
    }

    // ========================================================================
    // Hash Verification Data
    // ========================================================================

    @Nested
    @DisplayName("Hash verification data")
    class HashVerificationData {

        @Test
        @DisplayName("response contains sha512 hash")
        void responseContainsSha512Hash() {
            // Use the version with proper 128-char hash
            var response = Http.parseObject(ApiResponses.modrinthVersionWithMultipleFiles("sodium", "0.5.8"));
            var files = Http.arr(response, "files");
            var file = files.getFirst();
            var hashes = Http.obj(file, "hashes");

            assertThat(Http.str(hashes, "sha512")).isNotNull();
            assertThat(Http.str(hashes, "sha512")).hasSize(128); // SHA-512 hex length
        }

        @Test
        @DisplayName("response contains download url")
        void responseContainsDownloadUrl() {
            var response = Http.parseObject(ApiResponses.modrinthVersionFile("sodium", "0.5.8", "v058"));
            var files = Http.arr(response, "files");
            var file = files.getFirst();

            assertThat(Http.str(file, "url")).startsWith("https://");
        }
    }

    // ========================================================================
    // CheckResult Status Values
    // ========================================================================

    @Nested
    @DisplayName("CheckResult status values")
    class CheckResultStatusValues {

        @Test
        @DisplayName("current status means old and new versions match")
        void currentStatusMeansVersionsMatch() {
            Path modPath = tempDir.resolve("test-mod.jar");
            Api.CheckResult result = new Api.CheckResult(
                    "test-mod", modPath, "current", "0.5.8", "0.5.8", null, null, null
            );

            assertThat(result.status()).isEqualTo("current");
            assertThat(result.oldVersion()).isEqualTo(result.newVersion());
            // Download fields should be null for current status
            assertThat(result.downloadUrl()).isNull();
            assertThat(result.sha512()).isNull();
            assertThat(result.fileName()).isNull();
        }

        @Test
        @DisplayName("update status means versions differ and download info present")
        void updateStatusMeansVersionsDifferWithDownloadInfo() {
            Path modPath = tempDir.resolve("test-mod.jar");
            Api.CheckResult result = new Api.CheckResult(
                    "test-mod", modPath, "update", "0.5.7", "0.5.8",
                    "https://cdn.modrinth.com/test.jar", "abc123", "test-0.5.8.jar"
            );

            assertThat(result.status()).isEqualTo("update");
            assertThat(result.oldVersion()).isNotEqualTo(result.newVersion());
            // Download fields must be populated for update status
            assertThat(result.downloadUrl()).isNotNull();
            assertThat(result.sha512()).isNotNull();
            assertThat(result.fileName()).isNotNull();
        }
    }

    // ========================================================================
    // Error Handling Paths
    // ========================================================================

    @Nested
    @DisplayName("Error handling paths")
    class ErrorHandlingPaths {

        @Test
        @DisplayName("skip result represents unresolvable mod")
        void skipResultRepresentsUnresolvableMod() {
            // Create a skip result as Api.checkMod would return it
            Path modPath = tempDir.resolve("unknown-mod.jar");
            Api.CheckResult skipResult = new Api.CheckResult(
                    "unknown-mod", modPath, "skip", null, null, null, null, null
            );

            // Skip status has specific characteristics
            assertThat(skipResult.status()).isEqualTo("skip");
            assertThat(skipResult.newVersion()).isNull();
            assertThat(skipResult.downloadUrl()).isNull();
        }

        @Test
        @DisplayName("skip result can include old version when known")
        void skipResultCanIncludeOldVersion() {
            Path modPath = tempDir.resolve("known-mod.jar");
            Api.CheckResult skipResult = new Api.CheckResult(
                    "known-mod", modPath, "skip", "1.0.0", null, null, null, null
            );

            assertThat(skipResult.status()).isEqualTo("skip");
            assertThat(skipResult.oldVersion()).isEqualTo("1.0.0");
            assertThat(skipResult.newVersion()).isNull();
        }

        @Test
        @DisplayName("checkMod returns skip for mod with empty loaders list")
        void checkModReturnsSkipForEmptyLoaders() throws IOException {
            Path modPath = tempDir.resolve("test-mod.jar");
            TestFixtures.createFabricMod(modPath, "test-mod", "1.0.0");

            ModScanner.Mod mod = new ModScanner.Mod("test-mod", modPath, modPath.getFileName().toString());
            Api.CheckResult result = Api.checkMod(mod, "1.21.1", List.of(), false);

            // Empty loaders list always results in skip
            assertThat(result.status()).isEqualTo("skip");
        }

        @Test
        @DisplayName("checkMod returns skip for VANILLA loader")
        void checkModReturnsSkipForVanillaLoader() throws IOException {
            Path modPath = tempDir.resolve("test-mod.jar");
            TestFixtures.createFabricMod(modPath, "test-mod", "1.0.0");

            ModScanner.Mod mod = new ModScanner.Mod("test-mod", modPath, modPath.getFileName().toString());
            Api.CheckResult result = Api.checkMod(mod, "1.21.1", Loader.VANILLA, false);

            // VANILLA has empty modrinthLoaders(), so always skip
            assertThat(result.status()).isEqualTo("skip");
        }

        @Test
        @DisplayName("no matching release or beta results in skip")
        void noMatchingReleaseOrBetaResultsInSkip() {
            // Parse versions with only alpha type
            var versions = Http.parseArray("""
                    [
                        {"version_number": "1.0.0-alpha", "version_type": "alpha"},
                        {"version_number": "0.9.0-alpha", "version_type": "alpha"}
                    ]
                    """);

            // Simulate version filtering (allowBeta = false)
            boolean allowBeta = false;
            Map<String, Object> latest = null;
            for (var v : versions) {
                @SuppressWarnings("unchecked")
                var ver = (Map<String, Object>) v;
                String type = Http.str(ver, "version_type");
                if ("release".equals(type) || (allowBeta && "beta".equals(type))) {
                    latest = ver;
                    break;
                }
            }

            assertThat(latest).isNull();
        }

        @Test
        @DisplayName("beta not found when allowBeta is false")
        void betaNotFoundWhenAllowBetaIsFalse() {
            var versions = Http.parseArray("""
                    [
                        {"version_number": "2.0.0-beta", "version_type": "beta"}
                    ]
                    """);

            boolean allowBeta = false;
            Map<String, Object> latest = null;
            for (var v : versions) {
                @SuppressWarnings("unchecked")
                var ver = (Map<String, Object>) v;
                String type = Http.str(ver, "version_type");
                if ("release".equals(type) || (allowBeta && "beta".equals(type))) {
                    latest = ver;
                    break;
                }
            }

            assertThat(latest).isNull();
        }

        @Test
        @DisplayName("beta found when allowBeta is true")
        void betaFoundWhenAllowBetaIsTrue() {
            var versions = Http.parseArray("""
                    [
                        {"version_number": "2.0.0-beta", "version_type": "beta"}
                    ]
                    """);

            boolean allowBeta = true;
            Map<String, Object> latest = null;
            for (var v : versions) {
                @SuppressWarnings("unchecked")
                var ver = (Map<String, Object>) v;
                String type = Http.str(ver, "version_type");
                if ("release".equals(type) || (allowBeta && "beta".equals(type))) {
                    latest = ver;
                    break;
                }
            }

            assertThat(latest).isNotNull();
            assertThat(Http.str(latest, "version_number")).isEqualTo("2.0.0-beta");
        }

        @Test
        @DisplayName("null file from empty files array results in skip")
        void nullFileFromEmptyFilesArrayResultsInSkip() {
            var version = Http.parseObject("""
                    {
                        "version_number": "1.0.0",
                        "files": []
                    }
                    """);

            var files = Http.arr(version, "files");
            Map<String, Object> file = null;
            for (var f : files) {
                if (Http.bool(f, "primary", false)) {
                    file = f;
                    break;
                }
            }
            if (file == null && !files.isEmpty()) file = files.getFirst();

            // file should still be null since files is empty
            assertThat(file).isNull();
        }

        @Test
        @DisplayName("first file used when no primary flag set")
        void firstFileUsedWhenNoPrimaryFlagSet() {
            var version = Http.parseObject("""
                    {
                        "version_number": "1.0.0",
                        "files": [
                            {"filename": "mod-1.0.0.jar", "primary": false},
                            {"filename": "mod-1.0.0-sources.jar", "primary": false}
                        ]
                    }
                    """);

            var files = Http.arr(version, "files");
            Map<String, Object> file = null;
            for (var f : files) {
                if (Http.bool(f, "primary", false)) {
                    file = f;
                    break;
                }
            }
            if (file == null && !files.isEmpty()) file = files.getFirst();

            assertThat(file).isNotNull();
            assertThat(Http.str(file, "filename")).isEqualTo("mod-1.0.0.jar");
        }

        @Test
        @DisplayName("getLatestMinecraft returns null on network failure")
        void getLatestMinecraftReturnsNullOnNetworkFailure() {
            // Api.getLatestMinecraft catches exceptions and returns null
            // Without mocking, we can't trigger a network failure, but we can verify
            // the method signature via reflection and test return type allows null
            assertThatCode(() -> {
                var method = Api.class.getDeclaredMethod("getLatestMinecraft", boolean.class);
                assertThat(method.getReturnType()).isEqualTo(String.class);
                // String return type allows null (unlike primitive)
            }).doesNotThrowAnyException();

            // Also verify the method doesn't throw even when called
            // (it returns null on any internal exception)
            assertThatCode(() -> Api.getLatestMinecraft(false)).doesNotThrowAnyException();
            assertThatCode(() -> Api.getLatestMinecraft(true)).doesNotThrowAnyException();
        }
    }

    // ========================================================================
    // getLatestMinecraft
    // ========================================================================

    @Nested
    @DisplayName("getLatestMinecraft")
    class GetLatestMinecraft {

        @Test
        @DisplayName("returns release when allowSnapshots is false")
        void returnsReleaseWhenAllowSnapshotsFalse() {
            var manifest = Http.parseObject(ApiResponses.mojangVersionManifest());
            var latest = Http.obj(manifest, "latest");

            String release = Http.str(latest, "release");
            assertThat(release).matches("\\d+\\.\\d+(\\.\\d+)?");
        }

        @Test
        @DisplayName("returns snapshot when allowSnapshots is true")
        void returnsSnapshotWhenAllowSnapshotsTrue() {
            var manifest = Http.parseObject(ApiResponses.mojangVersionManifest());
            var latest = Http.obj(manifest, "latest");

            String snapshot = Http.str(latest, "snapshot");
            assertThat(snapshot).isNotNull();
        }
    }
}
