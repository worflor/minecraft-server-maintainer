package dev.woflo.fabric;

/**
 * Pre-recorded API response templates for testing.
 * These simulate responses from Mojang, Modrinth, Fabric Meta, Paper API, etc.
 */
public final class ApiResponses {

    private ApiResponses() {
        // Utility class
    }

    // ========================================================================
    // Mojang API Responses
    // ========================================================================

    /**
     * Version manifest response from launchermeta.mojang.com.
     */
    public static String mojangVersionManifest() {
        return """
                {
                    "latest": {
                        "release": "1.21.1",
                        "snapshot": "24w38a"
                    },
                    "versions": [
                        {
                            "id": "24w38a",
                            "type": "snapshot",
                            "url": "https://piston-meta.mojang.com/v1/packages/abc123/24w38a.json",
                            "releaseTime": "2024-09-18T12:00:00+00:00"
                        },
                        {
                            "id": "1.21.1",
                            "type": "release",
                            "url": "https://piston-meta.mojang.com/v1/packages/def456/1.21.1.json",
                            "releaseTime": "2024-08-08T10:00:00+00:00"
                        },
                        {
                            "id": "1.21",
                            "type": "release",
                            "url": "https://piston-meta.mojang.com/v1/packages/ghi789/1.21.json",
                            "releaseTime": "2024-06-13T10:00:00+00:00"
                        },
                        {
                            "id": "1.20.6",
                            "type": "release",
                            "url": "https://piston-meta.mojang.com/v1/packages/jkl012/1.20.6.json",
                            "releaseTime": "2024-04-29T12:00:00+00:00"
                        }
                    ]
                }
                """;
    }

    // Valid SHA-1 hash for Mojang API (40 hex characters)
    private static final String MOJANG_SHA1 = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";

    /**
     * Version details response for a specific Minecraft version.
     */
    public static String mojangVersionDetails(String version) {
        return """
                {
                    "id": "%s",
                    "type": "release",
                    "downloads": {
                        "server": {
                            "sha1": "%s",
                            "size": 50000000,
                            "url": "https://piston-data.mojang.com/v1/objects/abc123/server.jar"
                        }
                    }
                }
                """.formatted(version, MOJANG_SHA1);
    }

    // ========================================================================
    // Modrinth API Responses
    // ========================================================================

    // Valid SHA-512 hashes must be exactly 128 hex characters (512 bits = 64 bytes = 128 hex chars)
    private static final String HASH_CURRENT = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String HASH_NEW = "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5";
    private static final String HASH_OLD = "c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6";
    private static final String HASH_BETA = "d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1";
    private static final String HASH_RELEASE = "e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    private static final String HASH_FILE1 = "f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3";
    private static final String HASH_FILE2 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    // Valid SHA-1 hashes must be exactly 40 hex characters (160 bits = 20 bytes = 40 hex chars)
    private static final String SHA1_CURRENT = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    private static final String SHA1_NEW = "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3";
    private static final String SHA1_OLD = "c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4";
    private static final String SHA1_BETA = "d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5";
    private static final String SHA1_RELEASE = "e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6";
    private static final String SHA1_FILE1 = "f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1";
    private static final String SHA1_FILE2 = "0123456789abcdef0123456789abcdef01234567";
    private static final String SHA1_PRIMARY = "1234567890abcdef1234567890abcdef12345678";
    private static final String SHA1_SOURCE = "abcdef1234567890abcdef1234567890abcdef12";

    /**
     * Version file lookup response (by hash).
     */
    public static String modrinthVersionFile(String projectId, String versionNumber, String versionId) {
        return """
                {
                    "id": "%s",
                    "project_id": "%s",
                    "version_number": "%s",
                    "version_type": "release",
                    "loaders": ["fabric", "quilt"],
                    "game_versions": ["1.21.1", "1.21"],
                    "files": [
                        {
                            "hashes": {
                                "sha512": "%s",
                                "sha1": "%s"
                            },
                            "url": "https://cdn.modrinth.com/data/%s/versions/%s/mod.jar",
                            "filename": "mod-%s.jar",
                            "primary": true,
                            "size": 1000000
                        }
                    ]
                }
                """.formatted(versionId, projectId, versionNumber, HASH_CURRENT, SHA1_CURRENT, projectId, versionId, versionNumber);
    }

    /**
     * Project version list response.
     */
    public static String modrinthProjectVersions(String projectId, String latestVersion) {
        return """
                [
                    {
                        "id": "version123",
                        "project_id": "%s",
                        "version_number": "%s",
                        "version_type": "release",
                        "loaders": ["fabric", "quilt"],
                        "game_versions": ["1.21.1"],
                        "files": [
                            {
                                "hashes": {
                                    "sha512": "%s",
                                    "sha1": "%s"
                                },
                                "url": "https://cdn.modrinth.com/data/%s/versions/version123/mod-%s.jar",
                                "filename": "mod-%s.jar",
                                "primary": true,
                                "size": 1100000
                            }
                        ]
                    },
                    {
                        "id": "version122",
                        "project_id": "%s",
                        "version_number": "0.5.7",
                        "version_type": "release",
                        "loaders": ["fabric", "quilt"],
                        "game_versions": ["1.21"],
                        "files": [
                            {
                                "hashes": {
                                    "sha512": "%s",
                                    "sha1": "%s"
                                },
                                "url": "https://cdn.modrinth.com/data/%s/versions/version122/mod-0.5.7.jar",
                                "filename": "mod-0.5.7.jar",
                                "primary": true,
                                "size": 1050000
                            }
                        ]
                    }
                ]
                """.formatted(projectId, latestVersion, HASH_NEW, SHA1_NEW, projectId, latestVersion, latestVersion, projectId, HASH_OLD, SHA1_OLD, projectId);
    }

    /**
     * Empty version list (no compatible versions found).
     */
    public static String modrinthEmptyVersions() {
        return "[]";
    }

    /**
     * Modrinth API error response.
     */
    public static String modrinthError(String message) {
        return """
                {
                    "error": "not_found",
                    "description": "%s"
                }
                """.formatted(message);
    }

    // ========================================================================
    // Fabric Meta API Responses
    // ========================================================================

    /**
     * Fabric game versions list.
     */
    public static String fabricGameVersions() {
        return """
                [
                    {"version": "1.21.1", "stable": true},
                    {"version": "1.21", "stable": true},
                    {"version": "24w38a", "stable": false},
                    {"version": "1.20.6", "stable": true},
                    {"version": "1.20.4", "stable": true}
                ]
                """;
    }

    /**
     * Fabric loader versions for a specific game version.
     */
    public static String fabricLoaderVersions(String mcVersion) {
        return """
                [
                    {
                        "loader": {"version": "0.16.5", "stable": true},
                        "intermediary": {"version": "%s"},
                        "launcherMeta": {"version": 1}
                    },
                    {
                        "loader": {"version": "0.16.4", "stable": true},
                        "intermediary": {"version": "%s"},
                        "launcherMeta": {"version": 1}
                    }
                ]
                """.formatted(mcVersion, mcVersion);
    }

    /**
     * Empty loader list (version not supported).
     */
    public static String fabricEmptyLoaders() {
        return "[]";
    }

    /**
     * Fabric installer versions.
     */
    public static String fabricInstallerVersions() {
        return """
                [
                    {
                        "url": "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar",
                        "maven": "net.fabricmc:fabric-installer:1.0.1",
                        "version": "1.0.1",
                        "stable": true
                    }
                ]
                """;
    }

    // ========================================================================
    // Quilt Meta API Responses
    // ========================================================================

    /**
     * Quilt game versions list.
     */
    public static String quiltGameVersions() {
        return """
                [
                    {"version": "1.21.1", "stable": true},
                    {"version": "1.21", "stable": true},
                    {"version": "1.20.6", "stable": true}
                ]
                """;
    }

    /**
     * Quilt loader versions.
     */
    public static String quiltLoaderVersions(String mcVersion) {
        return """
                [
                    {
                        "loader": {"version": "0.26.4"},
                        "hashed": {"version": "%s"}
                    }
                ]
                """.formatted(mcVersion);
    }

    /**
     * Quilt installer versions.
     */
    public static String quiltInstallerVersions() {
        return """
                [
                    {
                        "url": "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/1.0.0/quilt-installer-1.0.0.jar",
                        "maven": "org.quiltmc:quilt-installer:1.0.0",
                        "version": "1.0.0"
                    }
                ]
                """;
    }

    // ========================================================================
    // Forge API Responses
    // ========================================================================

    /**
     * Forge promotions slim response.
     */
    public static String forgePromotions() {
        return """
                {
                    "homepage": "https://files.minecraftforge.net/",
                    "promos": {
                        "1.21.1-latest": "52.0.24",
                        "1.21.1-recommended": "52.0.20",
                        "1.21-latest": "51.0.33",
                        "1.21-recommended": "51.0.30",
                        "1.20.6-latest": "50.1.0",
                        "1.20.4-latest": "49.0.50"
                    }
                }
                """;
    }

    // ========================================================================
    // NeoForge API Responses
    // ========================================================================

    /**
     * NeoForge Maven versions response.
     */
    public static String neoforgeVersions() {
        return """
                {
                    "versions": [
                        "21.1.72",
                        "21.1.71",
                        "21.1.70",
                        "21.0.167",
                        "21.0.166",
                        "20.6.119",
                        "20.6.118"
                    ]
                }
                """;
    }

    // ========================================================================
    // Paper API Responses
    // ========================================================================

    /**
     * Paper project info.
     */
    public static String paperProjectInfo(String project) {
        return """
                {
                    "project_id": "%s",
                    "project_name": "%s",
                    "version_groups": ["1.21", "1.20"],
                    "versions": ["1.21.1", "1.21", "1.20.6", "1.20.4"]
                }
                """.formatted(project, capitalize(project));
    }

    // Valid SHA-256 hashes must be exactly 64 hex characters (256 bits = 32 bytes = 64 hex chars)
    private static final String PAPER_SHA256_1 = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
    private static final String PAPER_SHA256_2 = "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3";

    /**
     * Paper builds for a version.
     */
    public static String paperBuilds(String project, String version) {
        return """
                {
                    "project_id": "%s",
                    "project_name": "%s",
                    "version": "%s",
                    "builds": [
                        {
                            "build": 130,
                            "time": "2024-09-15T10:00:00.000Z",
                            "channel": "default",
                            "promoted": true,
                            "downloads": {
                                "application": {
                                    "name": "%s-%s-130.jar",
                                    "sha256": "%s"
                                }
                            }
                        },
                        {
                            "build": 129,
                            "time": "2024-09-14T10:00:00.000Z",
                            "channel": "default",
                            "promoted": false,
                            "downloads": {
                                "application": {
                                    "name": "%s-%s-129.jar",
                                    "sha256": "%s"
                                }
                            }
                        }
                    ]
                }
                """.formatted(project, capitalize(project), version, project, version, PAPER_SHA256_1, project, version, PAPER_SHA256_2);
    }

    /**
     * Empty builds response.
     */
    public static String paperEmptyBuilds(String project, String version) {
        return """
                {
                    "project_id": "%s",
                    "project_name": "%s",
                    "version": "%s",
                    "builds": []
                }
                """.formatted(project, capitalize(project), version);
    }

    // ========================================================================
    // Purpur API Responses
    // ========================================================================

    /**
     * Purpur version info.
     */
    public static String purpurVersionInfo(String version) {
        return """
                {
                    "project": "purpur",
                    "version": "%s",
                    "builds": {
                        "latest": "2150",
                        "all": ["2150", "2149", "2148"]
                    }
                }
                """.formatted(version);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Creates a version file response for a specific mod.
     */
    public static String modrinthModVersionFile(String modId, String currentVersion) {
        return modrinthVersionFile(modId, currentVersion, "v" + currentVersion.replace(".", ""));
    }

    /**
     * Creates a project versions response showing an available update.
     */
    public static String modrinthModWithUpdate(String modId, String currentVersion, String newVersion) {
        return """
                [
                    {
                        "id": "new_version_id",
                        "project_id": "%s",
                        "version_number": "%s",
                        "version_type": "release",
                        "loaders": ["fabric", "quilt"],
                        "game_versions": ["1.21.1"],
                        "files": [
                            {
                                "hashes": {
                                    "sha512": "%s",
                                    "sha1": "%s"
                                },
                                "url": "https://cdn.modrinth.com/data/%s/versions/new_version_id/%s-%s.jar",
                                "filename": "%s-%s.jar",
                                "primary": true,
                                "size": 1200000
                            }
                        ]
                    }
                ]
                """.formatted(modId, newVersion, HASH_NEW, SHA1_NEW, modId, modId, newVersion, modId, newVersion);
    }

    /**
     * Beta version file response.
     */
    public static String modrinthBetaVersionFile(String projectId, String versionNumber, String versionId) {
        return """
                {
                    "id": "%s",
                    "project_id": "%s",
                    "version_number": "%s",
                    "version_type": "beta",
                    "loaders": ["fabric", "quilt"],
                    "game_versions": ["1.21.1"],
                    "files": [
                        {
                            "hashes": {
                                "sha512": "%s",
                                "sha1": "%s"
                            },
                            "url": "https://cdn.modrinth.com/data/%s/versions/%s/mod-beta.jar",
                            "filename": "mod-%s-beta.jar",
                            "primary": true,
                            "size": 950000
                        }
                    ]
                }
                """.formatted(versionId, projectId, versionNumber, HASH_BETA, SHA1_BETA, projectId, versionId, versionNumber);
    }

    /**
     * Version list with both release and beta versions.
     */
    public static String modrinthVersionsWithBeta(String projectId, String releaseVersion, String betaVersion) {
        return """
                [
                    {
                        "id": "release_id",
                        "project_id": "%s",
                        "version_number": "%s",
                        "version_type": "release",
                        "loaders": ["fabric"],
                        "game_versions": ["1.21.1"],
                        "files": [
                            {
                                "hashes": {"sha512": "%s", "sha1": "%s"},
                                "url": "https://cdn.modrinth.com/release.jar",
                                "filename": "mod-release.jar",
                                "primary": true
                            }
                        ]
                    },
                    {
                        "id": "beta_id",
                        "project_id": "%s",
                        "version_number": "%s",
                        "version_type": "beta",
                        "loaders": ["fabric"],
                        "game_versions": ["1.21.1"],
                        "files": [
                            {
                                "hashes": {"sha512": "%s", "sha1": "%s"},
                                "url": "https://cdn.modrinth.com/beta.jar",
                                "filename": "mod-beta.jar",
                                "primary": true
                            }
                        ]
                    }
                ]
                """.formatted(projectId, releaseVersion, HASH_RELEASE, SHA1_RELEASE, projectId, betaVersion, HASH_BETA, SHA1_BETA);
    }

    /**
     * Version response with multiple files.
     */
    public static String modrinthVersionWithMultipleFiles(String projectId, String version) {
        // SHA-512 hash is 128 hex characters (512 bits = 64 bytes = 128 hex chars)
        String validSha512 = "a".repeat(128);
        return """
                {
                    "id": "multi_file_version",
                    "project_id": "%s",
                    "version_number": "%s",
                    "version_type": "release",
                    "loaders": ["fabric"],
                    "game_versions": ["1.21.1"],
                    "files": [
                        {
                            "hashes": {"sha512": "%s", "sha1": "%s"},
                            "url": "https://cdn.modrinth.com/primary.jar",
                            "filename": "%s-%s.jar",
                            "primary": true,
                            "size": 1000000
                        },
                        {
                            "hashes": {"sha512": "%s", "sha1": "%s"},
                            "url": "https://cdn.modrinth.com/sources.jar",
                            "filename": "%s-%s-sources.jar",
                            "primary": false,
                            "size": 500000
                        }
                    ]
                }
                """.formatted(projectId, version, validSha512, SHA1_PRIMARY, projectId, version, validSha512, SHA1_SOURCE, projectId, version);
    }

    /**
     * Version response with no primary file flag.
     */
    public static String modrinthVersionNoPrimaryFile(String projectId, String version) {
        return """
                {
                    "id": "no_primary_version",
                    "project_id": "%s",
                    "version_number": "%s",
                    "version_type": "release",
                    "loaders": ["fabric"],
                    "game_versions": ["1.21.1"],
                    "files": [
                        {
                            "hashes": {"sha512": "%s", "sha1": "%s"},
                            "url": "https://cdn.modrinth.com/file1.jar",
                            "filename": "%s-%s-file1.jar",
                            "primary": false,
                            "size": 1000000
                        },
                        {
                            "hashes": {"sha512": "%s", "sha1": "%s"},
                            "url": "https://cdn.modrinth.com/file2.jar",
                            "filename": "%s-%s-file2.jar",
                            "primary": false,
                            "size": 1100000
                        }
                    ]
                }
                """.formatted(projectId, version, HASH_FILE1, SHA1_FILE1, projectId, version, HASH_FILE2, SHA1_FILE2, projectId, version);
    }
}
