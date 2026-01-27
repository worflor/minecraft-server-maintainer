package dev.woflo.fabric;

import dev.woflo.fabric.TestTags.NetworkTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Network smoke tests that verify real API connectivity and schema stability.
 *
 * These tests are EXCLUDED from normal test runs (tagged with @NetworkTest).
 * Run manually with: ./gradlew networkTest
 *
 * Purpose:
 * - Verify APIs are reachable
 * - Detect schema changes that would break the application
 * - Confirm known projects (fabric-api) still exist
 *
 * These tests may fail if:
 * - No internet connection
 * - APIs are temporarily down
 * - API schemas have changed (indicates app needs updating)
 */
@NetworkTest
@DisplayName("Network Smoke Tests")
class NetworkSmokeTest {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final String MOJANG_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    // ========================================================================
    // Mojang API Tests
    // ========================================================================

    @Nested
    @DisplayName("Mojang API")
    class MojangApiTests {

        @Test
        @DisplayName("Version manifest is reachable and has expected schema")
        void versionManifestIsReachableAndHasExpectedSchema() throws Exception {
            Map<String, Object> manifest = Http.getJson(MOJANG_MANIFEST);

            // Verify top-level structure
            assertThat(manifest).containsKey("latest");
            assertThat(manifest).containsKey("versions");

            // Verify "latest" has release and snapshot
            Map<String, Object> latest = Http.obj(manifest, "latest");
            assertThat(latest).isNotNull();
            assertThat(Http.str(latest, "release")).isNotNull().isNotEmpty();
            assertThat(Http.str(latest, "snapshot")).isNotNull().isNotEmpty();

            // Verify versions array is non-empty and has expected fields
            List<Map<String, Object>> versions = Http.arr(manifest, "versions");
            assertThat(versions).isNotNull().isNotEmpty();

            // Check first version has expected structure
            Map<String, Object> firstVersion = versions.get(0);
            assertThat(firstVersion).containsKeys("id", "type", "url", "releaseTime");
        }

        @Test
        @DisplayName("Latest release version is a valid version string")
        void latestReleaseVersionIsValid() throws Exception {
            String latest = Api.getLatestMinecraft(false);

            assertThat(latest)
                    .isNotNull()
                    .isNotEmpty()
                    .matches("\\d+\\.\\d+(\\.\\d+)?"); // e.g., "1.21" or "1.21.1"
        }
    }

    // ========================================================================
    // Modrinth API Tests
    // ========================================================================

    @Nested
    @DisplayName("Modrinth API")
    class ModrinthApiTests {

        @Test
        @DisplayName("fabric-api project exists and has expected schema")
        void fabricApiProjectExistsAndHasExpectedSchema() throws Exception {
            // fabric-api is the most popular Fabric mod - if it doesn't exist, something is very wrong
            Map<String, Object> project = Http.getJson(MODRINTH_API + "/project/fabric-api");

            // Verify essential fields exist
            assertThat(project).containsKey("id");
            assertThat(project).containsKey("slug");
            assertThat(project).containsKey("title");
            assertThat(project).containsKey("downloads");
            assertThat(project).containsKey("versions");

            // Verify it's actually fabric-api
            assertThat(Http.str(project, "slug")).isEqualTo("fabric-api");

            // Verify it has significant downloads (sanity check)
            Number downloads = (Number) project.get("downloads");
            assertThat(downloads.longValue()).isGreaterThan(1_000_000); // fabric-api has millions of downloads
        }

        @Test
        @DisplayName("fabric-api versions endpoint returns valid version list")
        @SuppressWarnings("unchecked")
        void fabricApiVersionsEndpointReturnsValidVersionList() throws Exception {
            List<Object> versions = Http.getJsonArray(MODRINTH_API + "/project/fabric-api/version");

            assertThat(versions).isNotNull().isNotEmpty();

            // Check first version has expected structure
            Map<String, Object> firstVersion = (Map<String, Object>) versions.get(0);
            assertThat(firstVersion).containsKeys("id", "project_id", "version_number", "version_type", "loaders", "game_versions", "files");

            // Verify files array has expected structure
            List<Map<String, Object>> files = Http.arr(firstVersion, "files");
            assertThat(files).isNotNull().isNotEmpty();

            Map<String, Object> firstFile = files.get(0);
            assertThat(firstFile).containsKeys("hashes", "url", "filename");

            // Verify hash structure
            Map<String, Object> hashes = Http.obj(firstFile, "hashes");
            assertThat(hashes).containsKey("sha512");

            // Verify SHA-512 hash is exactly 128 hex characters
            String sha512 = Http.str(hashes, "sha512");
            assertThat(sha512)
                    .isNotNull()
                    .hasSize(128)
                    .matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("Version file lookup by hash returns expected schema")
        @SuppressWarnings("unchecked")
        void versionFileLookupByHashReturnsExpectedSchema() throws Exception {
            // First get a valid hash from fabric-api
            List<Object> versions = Http.getJsonArray(MODRINTH_API + "/project/fabric-api/version?limit=1");
            Map<String, Object> version = (Map<String, Object>) versions.get(0);
            List<Map<String, Object>> files = Http.arr(version, "files");
            Map<String, Object> file = files.get(0);
            Map<String, Object> hashes = Http.obj(file, "hashes");
            String sha512 = Http.str(hashes, "sha512");

            // Now look up that hash
            Map<String, Object> versionByHash = Http.getJson(MODRINTH_API + "/version_file/" + sha512);

            // Verify expected fields
            assertThat(versionByHash).containsKeys("id", "project_id", "version_number", "files");
        }
    }

    // ========================================================================
    // Fabric Meta API Tests
    // ========================================================================

    @Nested
    @DisplayName("Fabric Meta API")
    class FabricMetaApiTests {

        private static final String FABRIC_META = "https://meta.fabricmc.net/v2";

        @Test
        @DisplayName("Game versions endpoint returns valid list")
        @SuppressWarnings("unchecked")
        void gameVersionsEndpointReturnsValidList() throws Exception {
            List<Object> versions = Http.getJsonArray(FABRIC_META + "/versions/game");

            assertThat(versions).isNotNull().isNotEmpty();

            // Check first version has expected structure
            Map<String, Object> firstVersion = (Map<String, Object>) versions.get(0);
            assertThat(firstVersion).containsKeys("version", "stable");
        }

        @Test
        @DisplayName("Loader versions for latest MC are available")
        @SuppressWarnings("unchecked")
        void loaderVersionsForLatestMcAreAvailable() throws Exception {
            String latestMc = Api.getLatestMinecraft(false);
            assertThat(latestMc).isNotNull();

            List<Object> loaders = Http.getJsonArray(FABRIC_META + "/versions/loader/" + Http.encode(latestMc));

            assertThat(loaders).isNotNull().isNotEmpty();

            // Check structure
            Map<String, Object> firstLoader = (Map<String, Object>) loaders.get(0);
            assertThat(firstLoader).containsKey("loader");

            Map<String, Object> loader = Http.obj(firstLoader, "loader");
            assertThat(loader).containsKeys("version", "stable");
        }
    }

    // ========================================================================
    // Paper API Tests
    // ========================================================================

    @Nested
    @DisplayName("Paper API")
    class PaperApiTests {

        private static final String PAPER_API = "https://api.papermc.io/v2";

        @Test
        @DisplayName("Paper project info returns expected schema")
        void paperProjectInfoReturnsExpectedSchema() throws Exception {
            Map<String, Object> project = Http.getJson(PAPER_API + "/projects/paper");

            assertThat(project).containsKeys("project_id", "project_name", "versions");
            assertThat(Http.str(project, "project_id")).isEqualTo("paper");

            List<Map<String, Object>> versions = Http.arr(project, "versions");
            assertThat(versions).isNotNull().isNotEmpty();
        }
    }
}
