package dev.woflo.fabric;

import dev.woflo.fabric.*;
import dev.woflo.fabric.TestTags.ApiTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

/**
 * API response parsing tests.
 * Tests parsing of Mojang, Modrinth, Fabric Meta, Paper API, and other API responses.
 * Uses pre-recorded API responses from ApiResponses class.
 */
@ApiTest
@DisplayName("API Mock Tests")
class ApiMockTest {

    @TempDir
    Path tempDir;

    // ========================================================================
    // HTTP GET Tests
    // ========================================================================

    @Nested
    @DisplayName("HTTP GET operations")
    class HttpGetOperations {

        @Test
        @DisplayName("getJson parses JSON response")
        void getJsonParsesJsonResponse() {
            // Test Http.parseObject directly with sample JSON
            // Note: Http.getJson uses hardcoded URLs, so we test the parsing layer
            String response = """
                    {
                        "key": "value",
                        "number": 42,
                        "nested": {"inner": true}
                    }
                    """;

            var parsed = Http.parseObject(response);
            assertThat(Http.str(parsed, "key")).isEqualTo("value");
            assertThat(parsed.get("number")).isEqualTo(42.0);
            var nested = Http.obj(parsed, "nested");
            assertThat(Http.bool(nested, "inner", false)).isTrue();
        }

        @Test
        @DisplayName("getJsonArray parses array response")
        void getJsonArrayParsesArrayResponse() {
            String response = """
                    [
                        {"id": 1, "name": "first"},
                        {"id": 2, "name": "second"}
                    ]
                    """;

            var parsed = Http.parseArray(response);
            assertThat(parsed).hasSize(2);
        }
    }

    // ========================================================================
    // Mojang API Response Parsing
    // ========================================================================

    @Nested
    @DisplayName("Mojang API responses")
    class MojangApiResponses {

        @Test
        @DisplayName("parses version manifest")
        void parsesVersionManifest() {
            var parsed = Http.parseObject(ApiResponses.mojangVersionManifest());

            var latest = Http.obj(parsed, "latest");
            assertThat(Http.str(latest, "release")).isEqualTo("1.21.1");
            assertThat(Http.str(latest, "snapshot")).isEqualTo("24w38a");

            var versions = Http.arr(parsed, "versions");
            assertThat(versions).hasSizeGreaterThan(0);
        }

        @Test
        @DisplayName("extracts latest release version")
        void extractsLatestReleaseVersion() {
            var manifest = Http.parseObject(ApiResponses.mojangVersionManifest());
            var latest = Http.obj(manifest, "latest");
            String release = Http.str(latest, "release");

            assertThat(release).isEqualTo("1.21.1");
        }

        @Test
        @DisplayName("extracts latest snapshot version")
        void extractsLatestSnapshotVersion() {
            var manifest = Http.parseObject(ApiResponses.mojangVersionManifest());
            var latest = Http.obj(manifest, "latest");
            String snapshot = Http.str(latest, "snapshot");

            assertThat(snapshot).isEqualTo("24w38a");
        }

        @Test
        @DisplayName("parses version details")
        void parsesVersionDetails() {
            var details = Http.parseObject(ApiResponses.mojangVersionDetails("1.21.1"));

            assertThat(Http.str(details, "id")).isEqualTo("1.21.1");

            var downloads = Http.obj(details, "downloads");
            var server = Http.obj(downloads, "server");
            assertThat(Http.str(server, "url")).isNotNull();
        }
    }

    // ========================================================================
    // Modrinth API Response Parsing
    // ========================================================================

    @Nested
    @DisplayName("Modrinth API responses")
    class ModrinthApiResponses {

        @Test
        @DisplayName("parses version file response")
        void parsesVersionFileResponse() {
            var response = Http.parseObject(ApiResponses.modrinthVersionFile("sodium", "0.5.8", "v058"));

            assertThat(Http.str(response, "project_id")).isEqualTo("sodium");
            assertThat(Http.str(response, "version_number")).isEqualTo("0.5.8");
            assertThat(Http.str(response, "version_type")).isEqualTo("release");

            var files = Http.arr(response, "files");
            assertThat(files).hasSize(1);

            var file = files.get(0);
            assertThat(Http.bool(file, "primary", false)).isTrue();
            assertThat(Http.str(file, "url")).isNotNull();

            var hashes = Http.obj(file, "hashes");
            assertThat(Http.str(hashes, "sha512")).isNotNull();
        }

        @Test
        @DisplayName("parses project versions response")
        void parsesProjectVersionsResponse() {
            var versions = Http.parseArray(ApiResponses.modrinthProjectVersions("sodium", "0.5.8"));

            assertThat(versions).hasSizeGreaterThan(0);

            @SuppressWarnings("unchecked")
            var latest = (java.util.Map<String, Object>) versions.get(0);
            assertThat(Http.str(latest, "version_number")).isEqualTo("0.5.8");
            assertThat(Http.str(latest, "version_type")).isEqualTo("release");
        }

        @Test
        @DisplayName("handles empty versions list")
        void handlesEmptyVersionsList() {
            var versions = Http.parseArray(ApiResponses.modrinthEmptyVersions());

            assertThat(versions).isEmpty();
        }

        @Test
        @DisplayName("detects update available")
        void detectsUpdateAvailable() {
            var currentFile = Http.parseObject(ApiResponses.modrinthVersionFile("sodium", "0.5.7", "v057"));
            var versions = Http.parseArray(ApiResponses.modrinthModWithUpdate("sodium", "0.5.7", "0.5.8"));

            String currentVersion = Http.str(currentFile, "version_number");
            assertThat(currentVersion).isEqualTo("0.5.7");

            @SuppressWarnings("unchecked")
            var latestVersion = (java.util.Map<String, Object>) versions.get(0);
            String newVersion = Http.str(latestVersion, "version_number");
            assertThat(newVersion).isEqualTo("0.5.8");

            assertThat(currentVersion).isNotEqualTo(newVersion);
        }
    }

    // ========================================================================
    // Fabric Meta API Response Parsing
    // ========================================================================

    @Nested
    @DisplayName("Fabric Meta API responses")
    class FabricMetaApiResponses {

        @Test
        @DisplayName("parses game versions")
        void parsesGameVersions() {
            var versions = Http.parseArray(ApiResponses.fabricGameVersions());

            assertThat(versions).hasSizeGreaterThan(0);

            @SuppressWarnings("unchecked")
            var latest = (java.util.Map<String, Object>) versions.get(0);
            assertThat(Http.str(latest, "version")).isEqualTo("1.21.1");
            assertThat(Http.bool(latest, "stable", false)).isTrue();
        }

        @Test
        @DisplayName("identifies stable versions")
        void identifiesStableVersions() {
            var versions = Http.parseArray(ApiResponses.fabricGameVersions());

            long stableCount = versions.stream()
                    .filter(v -> {
                        @SuppressWarnings("unchecked")
                        var map = (java.util.Map<String, Object>) v;
                        return Http.bool(map, "stable", false);
                    })
                    .count();

            assertThat(stableCount).isGreaterThan(0);
        }

        @Test
        @DisplayName("parses loader versions")
        void parsesLoaderVersions() {
            var loaders = Http.parseArray(ApiResponses.fabricLoaderVersions("1.21.1"));

            assertThat(loaders).hasSizeGreaterThan(0);

            @SuppressWarnings("unchecked")
            var first = (java.util.Map<String, Object>) loaders.get(0);
            var loader = Http.obj(first, "loader");
            assertThat(Http.str(loader, "version")).isNotNull();
        }

        @Test
        @DisplayName("parses installer versions")
        void parsesInstallerVersions() {
            var installers = Http.parseArray(ApiResponses.fabricInstallerVersions());

            assertThat(installers).hasSizeGreaterThan(0);

            @SuppressWarnings("unchecked")
            var first = (java.util.Map<String, Object>) installers.get(0);
            assertThat(Http.str(first, "url")).isNotNull();
            assertThat(Http.str(first, "version")).isNotNull();
        }
    }

    // ========================================================================
    // Quilt Meta API Response Parsing
    // ========================================================================

    @Nested
    @DisplayName("Quilt Meta API responses")
    class QuiltMetaApiResponses {

        @Test
        @DisplayName("parses game versions")
        void parsesGameVersions() {
            var versions = Http.parseArray(ApiResponses.quiltGameVersions());

            assertThat(versions).hasSizeGreaterThan(0);
        }

        @Test
        @DisplayName("parses loader versions")
        void parsesLoaderVersions() {
            var loaders = Http.parseArray(ApiResponses.quiltLoaderVersions("1.21.1"));

            assertThat(loaders).hasSizeGreaterThan(0);
        }
    }

    // ========================================================================
    // Forge API Response Parsing
    // ========================================================================

    @Nested
    @DisplayName("Forge API responses")
    class ForgeApiResponses {

        @Test
        @DisplayName("parses promotions")
        void parsesPromotions() {
            var response = Http.parseObject(ApiResponses.forgePromotions());

            var promos = Http.obj(response, "promos");
            assertThat(promos).isNotNull();
            assertThat(Http.str(promos, "1.21.1-latest")).isEqualTo("52.0.24");
            assertThat(Http.str(promos, "1.21.1-recommended")).isEqualTo("52.0.20");
        }

        @Test
        @DisplayName("checks version availability")
        void checksVersionAvailability() {
            var response = Http.parseObject(ApiResponses.forgePromotions());
            var promos = Http.obj(response, "promos");

            // Check if 1.21.1 is available
            boolean hasLatest = promos.containsKey("1.21.1-latest");
            assertThat(hasLatest).isTrue();
        }
    }

    // ========================================================================
    // NeoForge API Response Parsing
    // ========================================================================

    @Nested
    @DisplayName("NeoForge API responses")
    class NeoForgeApiResponses {

        @Test
        @DisplayName("parses versions list")
        void parsesVersionsList() {
            var response = Http.parseObject(ApiResponses.neoforgeVersions());

            var versions = Http.list(response, "versions");
            assertThat(versions).hasSizeGreaterThan(0);
            assertThat(versions.get(0).toString()).startsWith("21.");
        }

        @Test
        @DisplayName("finds version for MC version")
        void findsVersionForMcVersion() {
            var response = Http.parseObject(ApiResponses.neoforgeVersions());
            var versions = Http.list(response, "versions");

            // Find version for MC 1.21.1 (prefix 21.1.)
            String prefix = "21.1.";
            boolean found = versions.stream()
                    .anyMatch(v -> v.toString().startsWith(prefix));

            assertThat(found).isTrue();
        }
    }

    // ========================================================================
    // Paper API Response Parsing
    // ========================================================================

    @Nested
    @DisplayName("Paper API responses")
    class PaperApiResponses {

        @Test
        @DisplayName("parses project info")
        void parsesProjectInfo() {
            var info = Http.parseObject(ApiResponses.paperProjectInfo("paper"));

            assertThat(Http.str(info, "project_id")).isEqualTo("paper");

            var versions = Http.list(info, "versions");
            assertThat(versions).contains("1.21.1");
        }

        @Test
        @DisplayName("parses builds")
        void parsesBuilds() {
            var response = Http.parseObject(ApiResponses.paperBuilds("paper", "1.21.1"));

            var builds = Http.arr(response, "builds");
            assertThat(builds).hasSizeGreaterThan(0);

            var latest = builds.getLast();
            assertThat(latest.get("build")).isNotNull();

            var downloads = Http.obj(latest, "downloads");
            var app = Http.obj(downloads, "application");
            assertThat(Http.str(app, "name")).isNotNull();
        }

        @Test
        @DisplayName("handles empty builds")
        void handlesEmptyBuilds() {
            var response = Http.parseObject(ApiResponses.paperEmptyBuilds("paper", "1.99.0"));

            var builds = Http.arr(response, "builds");
            assertThat(builds).isEmpty();
        }
    }

    // ========================================================================
    // Purpur API Response Parsing
    // ========================================================================

    @Nested
    @DisplayName("Purpur API responses")
    class PurpurApiResponses {

        @Test
        @DisplayName("parses version info")
        void parsesVersionInfo() {
            var info = Http.parseObject(ApiResponses.purpurVersionInfo("1.21.1"));

            assertThat(Http.str(info, "project")).isEqualTo("purpur");
            assertThat(Http.str(info, "version")).isEqualTo("1.21.1");

            var builds = Http.obj(info, "builds");
            assertThat(Http.str(builds, "latest")).isNotNull();
        }
    }

    // ========================================================================
    // SHA-512 Hash Verification
    // ========================================================================

    @Nested
    @DisplayName("SHA-512 hash verification")
    class HashVerification {

        @Test
        @DisplayName("computes SHA-512 hash of file")
        void computesSha512Hash() throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "Hello, World!");

            String hash = Http.sha512(testFile);

            assertThat(hash)
                    .isNotBlank()
                    .hasSize(128); // SHA-512 = 512 bits = 128 hex chars
        }

        @Test
        @DisplayName("same content produces same hash")
        void sameContentSameHash() throws IOException {
            Path file1 = tempDir.resolve("file1.txt");
            Path file2 = tempDir.resolve("file2.txt");
            Files.writeString(file1, "Identical content");
            Files.writeString(file2, "Identical content");

            String hash1 = Http.sha512(file1);
            String hash2 = Http.sha512(file2);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("different content produces different hash")
        void differentContentDifferentHash() throws IOException {
            Path file1 = tempDir.resolve("file1.txt");
            Path file2 = tempDir.resolve("file2.txt");
            Files.writeString(file1, "Content A");
            Files.writeString(file2, "Content B");

            String hash1 = Http.sha512(file1);
            String hash2 = Http.sha512(file2);

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    // ========================================================================
    // URL Encoding
    // ========================================================================

    @Nested
    @DisplayName("URL encoding")
    class UrlEncoding {

        @Test
        @DisplayName("encodes special characters")
        void encodesSpecialCharacters() {
            String encoded = Http.encode("hello world");
            assertThat(encoded).isEqualTo("hello+world");
        }

        @Test
        @DisplayName("encodes JSON characters for query params")
        void encodesJsonCharacters() {
            String encoded = Http.encode("[\"1.21.1\"]");
            assertThat(encoded).contains("%5B").contains("%5D");
        }
    }
}
