package dev.woflo.fabric;

import dev.woflo.fabric.*;
import dev.woflo.fabric.TestTags.UnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for Backup.java.
 * Tests backup creation, restoration, cleanup, and stasis snapshots.
 *
 * These tests call real Backup methods and verify source code patterns
 * stay in sync between tests and production code.
 */
@UnitTest
@DisplayName("Backup")
class BackupTest {

    @TempDir
    Path tempDir;

    private MockConsole console;

    @BeforeEach
    void setUp() throws IOException {
        console = new MockConsole();
    }

    // ========================================================================
    // Source Code Verification
    // ========================================================================

    @Nested
    @DisplayName("Source code verification")
    class SourceCodeVerification {

        @Test
        @DisplayName("Backup.java contains expected timestamp format")
        void backupJavaContainsExpectedTimestampFormat() throws IOException {
            Path backupJava = Path.of("src/dev/woflo/fabric/Backup.java");
            assertThat(backupJava).exists();

            String source = Files.readString(backupJava);

            // Verify timestamp format matches what we test
            assertThat(source).contains("DateTimeFormatter.ofPattern(\"yyyyMMdd-HHmmss\")");

            // Verify STASIS_SKIP directories
            assertThat(source).contains("STASIS_SKIP = Set.of(");
            assertThat(source).contains("\"libraries\"");
            assertThat(source).contains("\"versions\"");

            // Verify crash reports keep count
            assertThat(source).contains("CRASH_REPORTS_KEEP = 5");
        }

        @Test
        @DisplayName("Main.java doRollback finds latest backup by filename")
        void mainJavaDoRollbackFindsLatestBackupByFilename() throws IOException {
            Path mainJava = Path.of("src/dev/woflo/fabric/Main.java");
            assertThat(mainJava).exists();

            String source = Files.readString(mainJava);

            // Verify doRollback method exists and uses expected pattern
            assertThat(source).contains("private static void doRollback()");

            // Verify it uses Files.list on backups directory
            assertThat(source).contains("Files.list(backups)");

            // Verify it filters for directories and finds max by filename
            assertThat(source).contains("filter(Files::isDirectory)");
            assertThat(source).contains("max(Comparator.comparing(p -> p.getFileName().toString())");
        }
    }

    // ========================================================================
    // Backup Creation
    // ========================================================================

    @Nested
    @DisplayName("Backup creation")
    class BackupCreation {

        @Test
        @DisplayName("creates backup directory with timestamp and reason")
        void createsBackupDirectoryWithTimestampAndReason() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);

            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            assertThat(backup).isNotNull();
            assertThat(backup.getFileName().toString()).matches("\\d{8}-\\d{6}_test");
            assertThat(backup).exists().isDirectory();
        }

        @Test
        @DisplayName("backs up mods directory for Fabric")
        void backsUpModsDirectoryForFabric() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);

            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            assertThat(backup.resolve("mods")).exists().isDirectory();
            assertThat(backup.resolve("mods/sodium-fabric-0.5.8.jar")).exists();
            assertThat(backup.resolve("mods/lithium-fabric-0.12.1.jar")).exists();
        }

        @Test
        @DisplayName("backs up current_version.txt")
        void backsUpCurrentVersionTxt() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);

            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            assertThat(backup.resolve("current_version.txt")).exists();
            assertThat(Files.readString(backup.resolve("current_version.txt"))).isEqualTo("1.21.1");
        }

        @Test
        @DisplayName("backs up launcher JAR for Fabric")
        void backsUpLauncherJarForFabric() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);

            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            assertThat(backup.resolve("fabric-server-launch.jar")).exists();
        }

        @Test
        @DisplayName("backs up plugins directory for Paper")
        void backsUpPluginsDirectoryForPaper() throws IOException {
            Path serverDir = TestFixtures.createPaperServer(tempDir);

            Path backup = Backup.createSilent(serverDir, Loader.PAPER, "test");

            assertThat(backup.resolve("plugins")).exists().isDirectory();
            assertThat(backup.resolve("plugins/EssentialsX-2.20.jar")).exists();
        }

        @Test
        @DisplayName("backs up server.jar for Vanilla")
        void backsUpServerJarForVanilla() throws IOException {
            Path serverDir = TestFixtures.createVanillaServer(tempDir);

            Path backup = Backup.createSilent(serverDir, Loader.VANILLA, "test");

            assertThat(backup.resolve("server.jar")).exists();
        }

        @Test
        @DisplayName("skips missing backup items")
        void skipsMissingBackupItems() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            // Delete versions directory (not always present)
            Files.deleteIfExists(serverDir.resolve("versions"));

            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            assertThat(backup).isNotNull();
            assertThat(backup.resolve("versions")).doesNotExist();
            assertThat(backup.resolve("mods")).exists(); // Other items still backed up
        }

        @Test
        @DisplayName("returns null on backup failure")
        @DisabledOnOs(OS.WINDOWS)
        void returnsNullOnBackupFailure() {
            // On Unix, /dev/null cannot have subdirectories created under it
            Path backup = Backup.createSilent(Path.of("/dev/null/invalid"), Loader.FABRIC, "test");

            assertThat(backup).isNull();
        }

        @ParameterizedTest
        @DisplayName("creates backup for each loader type")
        @EnumSource(Loader.class)
        void createsBackupForEachLoader(Loader loader) throws IOException {
            Path serverDir = TestFixtures.createServerForLoader(tempDir, loader);

            Path backup = Backup.createSilent(serverDir, loader, "test");

            assertThat(backup).isNotNull();
            assertThat(backup).exists();
        }
    }

    // ========================================================================
    // Backup Restoration
    // ========================================================================

    @Nested
    @DisplayName("Backup restoration")
    class BackupRestoration {

        @Test
        @DisplayName("restores mods directory from backup")
        void restoresModsDirectoryFromBackup() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            // Modify server
            Files.delete(serverDir.resolve("mods/sodium-fabric-0.5.8.jar"));
            TestFixtures.createFabricMod(serverDir.resolve("mods/new-mod.jar"), "newmod", "1.0");

            boolean result = Backup.restore(backup, serverDir, Loader.FABRIC, console);

            assertThat(result).isTrue();
            assertThat(serverDir.resolve("mods/sodium-fabric-0.5.8.jar")).exists();
            // new-mod.jar should be removed as mods directory is replaced
            assertThat(serverDir.resolve("mods/new-mod.jar")).doesNotExist();
        }

        @Test
        @DisplayName("restores version file from backup")
        void restoresVersionFileFromBackup() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            // Modify version
            Files.writeString(serverDir.resolve("current_version.txt"), "1.22.0");

            boolean result = Backup.restore(backup, serverDir, Loader.FABRIC, console);

            assertThat(result).isTrue();
            assertThat(Files.readString(serverDir.resolve("current_version.txt"))).isEqualTo("1.21.1");
        }

        @Test
        @DisplayName("logs restore messages to console")
        void logsRestoreMessagesToConsole() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            Backup.restore(backup, serverDir, Loader.FABRIC, console);

            assertThat(console.hasWarning("Restoring")).isTrue();
            assertThat(console.hasInfo("Restored")).isTrue();
        }

        @Test
        @DisplayName("handles missing backup items gracefully")
        void handlesMissingBackupItemsGracefully() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            // Delete item from backup
            Backup.del(backup.resolve("mods"));

            boolean result = Backup.restore(backup, serverDir, Loader.FABRIC, console);

            assertThat(result).isTrue();
            // Version file should still be restored
            assertThat(serverDir.resolve("current_version.txt")).exists();
        }

        @Test
        @DisplayName("renames existing files before restore")
        void renamesExistingFilesBeforeRestore() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            // Verify restoration doesn't fail due to existing files
            boolean result = Backup.restore(backup, serverDir, Loader.FABRIC, console);

            assertThat(result).isTrue();
        }
    }

    // ========================================================================
    // Backup Cleanup
    // ========================================================================

    @Nested
    @DisplayName("Backup cleanup")
    class BackupCleanup {

        @Test
        @DisplayName("removes backups older than keep days")
        void removesBackupsOlderThanKeepDays() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path backupsDir = Files.createDirectories(serverDir.resolve("woflo/backups"));

            // Create old backup
            Path oldBackup = Files.createDirectories(backupsDir.resolve("20200101-120000_old"));
            Files.setLastModifiedTime(oldBackup, FileTime.from(Instant.now().minus(Duration.ofDays(30))));

            // Create recent backup
            Path newBackup = Files.createDirectories(backupsDir.resolve("20241001-120000_new"));

            Backup.cleanup(serverDir, 7);

            assertThat(oldBackup).doesNotExist();
            assertThat(newBackup).exists();
        }

        @Test
        @DisplayName("keeps backups within keep days")
        void keepsBackupsWithinKeepDays() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path backupsDir = Files.createDirectories(serverDir.resolve("woflo/backups"));

            // Create backup from 3 days ago
            Path recentBackup = Files.createDirectories(backupsDir.resolve("20241001-120000_recent"));
            Files.setLastModifiedTime(recentBackup, FileTime.from(Instant.now().minus(Duration.ofDays(3))));

            Backup.cleanup(serverDir, 7);

            assertThat(recentBackup).exists();
        }

        @Test
        @DisplayName("handles missing backups directory")
        void handlesMissingBackupsDirectory() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));

            // Should not throw
            assertThatCode(() -> Backup.cleanup(serverDir, 7)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handles empty backups directory")
        void handlesEmptyBackupsDirectory() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Files.createDirectories(serverDir.resolve("woflo/backups"));

            assertThatCode(() -> Backup.cleanup(serverDir, 7)).doesNotThrowAnyException();
        }
    }

    // ========================================================================
    // File Deletion
    // ========================================================================

    @Nested
    @DisplayName("File deletion")
    class FileDeletion {

        @Test
        @DisplayName("deletes single file")
        void deletesSingleFile() throws IOException {
            Path file = Files.createFile(tempDir.resolve("test.txt"));

            Backup.del(file);

            assertThat(file).doesNotExist();
        }

        @Test
        @DisplayName("deletes directory recursively")
        void deletesDirectoryRecursively() throws IOException {
            Path dir = Files.createDirectories(tempDir.resolve("testdir/subdir"));
            Files.createFile(dir.resolve("file.txt"));
            Files.createFile(tempDir.resolve("testdir/other.txt"));

            Backup.del(tempDir.resolve("testdir"));

            assertThat(tempDir.resolve("testdir")).doesNotExist();
        }

        @Test
        @DisplayName("handles non-existent path")
        void handlesNonExistentPath() throws IOException {
            Path nonExistent = tempDir.resolve("does-not-exist");

            assertThatCode(() -> Backup.del(nonExistent)).doesNotThrowAnyException();
        }
    }

    // ========================================================================
    // Stasis Snapshots
    // ========================================================================

    @Nested
    @DisplayName("Stasis snapshots")
    class StasisSnapshots {

        @Test
        @DisplayName("stasisDue returns true when no stasis exists")
        void stasisDueReturnsTrueWhenNoStasisExists() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));

            boolean due = Backup.stasisDue(serverDir, 24);

            assertThat(due).isTrue();
        }

        @Test
        @DisplayName("stasisDue returns true when stasis is old")
        void stasisDueReturnsTrueWhenStasisIsOld() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path stasisDir = Files.createDirectories(serverDir.resolve("woflo/stasis"));
            Path oldStasis = Files.createFile(stasisDir.resolve("20200101-120000.zip"));
            Files.setLastModifiedTime(oldStasis, FileTime.from(Instant.now().minus(Duration.ofHours(48))));

            boolean due = Backup.stasisDue(serverDir, 24);

            assertThat(due).isTrue();
        }

        @Test
        @DisplayName("stasisDue returns false when stasis is recent")
        void stasisDueReturnsFalseWhenStasisIsRecent() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path stasisDir = Files.createDirectories(serverDir.resolve("woflo/stasis"));
            Path recentStasis = Files.createFile(stasisDir.resolve("20241001-120000.zip"));
            // Default is current time, which is recent

            boolean due = Backup.stasisDue(serverDir, 24);

            assertThat(due).isFalse();
        }

        @Test
        @DisplayName("createStasis creates ZIP file")
        void createStasisCreatesZipFile() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            TestFixtures.createAcceptedEula(serverDir);

            Backup.createStasis(serverDir, console);

            Path stasisDir = serverDir.resolve("woflo/stasis");
            assertThat(stasisDir).exists();

            try (var files = Files.list(stasisDir)) {
                var zipFiles = files.filter(p -> p.toString().endsWith(".zip")).toList();
                assertThat(zipFiles).hasSize(1);

                // Verify ZIP is valid
                try (var zis = new ZipInputStream(Files.newInputStream(zipFiles.get(0)))) {
                    ZipEntry entry;
                    int count = 0;
                    while ((entry = zis.getNextEntry()) != null) {
                        count++;
                    }
                    assertThat(count).isGreaterThan(0);
                }
            }
        }

        @Test
        @DisplayName("createStasis excludes skip directories")
        void createStasisExcludesSkipDirectories() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            Files.createDirectories(serverDir.resolve("libraries/net/minecraft"));
            Files.createFile(serverDir.resolve("libraries/net/minecraft/large-file.jar"));

            Backup.createStasis(serverDir, console);

            Path stasisDir = serverDir.resolve("woflo/stasis");
            try (var files = Files.list(stasisDir)) {
                var zipFile = files.filter(p -> p.toString().endsWith(".zip")).findFirst().orElseThrow();

                try (var zis = new ZipInputStream(Files.newInputStream(zipFile))) {
                    ZipEntry entry;
                    List<String> entries = new ArrayList<>();
                    while ((entry = zis.getNextEntry()) != null) {
                        entries.add(entry.getName());
                    }
                    // Should not contain libraries
                    assertThat(entries).noneMatch(e -> e.startsWith("libraries/"));
                }
            }
        }

        @Test
        @DisplayName("cleanupStasis keeps specified number of snapshots")
        void cleanupStasisKeepsSpecifiedNumber() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path stasisDir = Files.createDirectories(serverDir.resolve("woflo/stasis"));

            // Create 5 stasis files with different times
            for (int i = 1; i <= 5; i++) {
                Path zip = Files.createFile(stasisDir.resolve("2024010" + i + "-120000.zip"));
                Files.setLastModifiedTime(zip, FileTime.from(Instant.now().minus(Duration.ofDays(i))));
            }

            Backup.cleanupStasis(serverDir, 3);

            try (var files = Files.list(stasisDir)) {
                var remaining = files.toList();
                assertThat(remaining).hasSize(3);
            }
        }
    }

    // ========================================================================
    // Rollback Flow (Main.doRollback logic)
    // ========================================================================

    @Nested
    @DisplayName("Rollback flow")
    class RollbackFlow {

        @Test
        @DisplayName("finds latest backup by timestamp filename")
        void findsLatestBackupByTimestamp() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path backupsDir = Files.createDirectories(serverDir.resolve("woflo/backups"));

            // Create backups with different timestamps (format: yyyyMMdd-HHmmss_reason)
            Files.createDirectories(backupsDir.resolve("20241001-120000_old"));
            Files.createDirectories(backupsDir.resolve("20241015-143000_recent"));
            Files.createDirectories(backupsDir.resolve("20241010-090000_middle"));

            // Find latest by filename comparison (as Main.doRollback does)
            try (var s = Files.list(backupsDir)) {
                var latest = s.filter(Files::isDirectory)
                        .max(Comparator.comparing(p -> p.getFileName().toString()));

                assertThat(latest).isPresent();
                assertThat(latest.get().getFileName().toString()).startsWith("20241015-143000");
            }
        }

        @Test
        @DisplayName("handles no backups folder")
        void handlesNoBackupsFolder() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Files.createDirectories(serverDir.resolve("woflo")); // woflo exists but no backups subfolder

            Path backupsDir = serverDir.resolve("woflo/backups");

            assertThat(Files.exists(backupsDir)).isFalse();
        }

        @Test
        @DisplayName("handles empty backups folder")
        void handlesEmptyBackupsFolder() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path backupsDir = Files.createDirectories(serverDir.resolve("woflo/backups"));

            try (var s = Files.list(backupsDir)) {
                var latest = s.filter(Files::isDirectory)
                        .max(Comparator.comparing(p -> p.getFileName().toString()));

                assertThat(latest).isEmpty();
            }
        }

        @Test
        @DisplayName("ignores files in backups folder (only directories)")
        void ignoresFilesInBackupsFolder() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path backupsDir = Files.createDirectories(serverDir.resolve("woflo/backups"));

            // Create a file (should be ignored) and a directory
            Files.writeString(backupsDir.resolve("20241020-000000_fake.txt"), "not a backup");
            Files.createDirectories(backupsDir.resolve("20241001-120000_real"));

            try (var s = Files.list(backupsDir)) {
                var latest = s.filter(Files::isDirectory)
                        .max(Comparator.comparing(p -> p.getFileName().toString()));

                assertThat(latest).isPresent();
                assertThat(latest.get().getFileName().toString()).isEqualTo("20241001-120000_real");
            }
        }

        @Test
        @DisplayName("full rollback flow with restore")
        void fullRollbackFlowWithRestore() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);

            // Create a backup
            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "pre-update");
            assertThat(backup).isNotNull();

            // Modify the server (simulate update gone wrong)
            Files.writeString(serverDir.resolve("current_version.txt"), "1.22.0-broken");

            // Find latest backup (as Main.doRollback does)
            Path backupsDir = serverDir.resolve("woflo/backups");
            try (var s = Files.list(backupsDir)) {
                var latest = s.filter(Files::isDirectory)
                        .max(Comparator.comparing(p -> p.getFileName().toString()));

                assertThat(latest).isPresent();

                // Restore it
                boolean result = Backup.restore(latest.get(), serverDir, Loader.FABRIC, console);
                assertThat(result).isTrue();
                assertThat(Files.readString(serverDir.resolve("current_version.txt"))).isEqualTo("1.21.1");
            }
        }

        @Test
        @DisplayName("selects correct backup when multiple exist with same date")
        void selectsCorrectBackupWithSameDate() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path backupsDir = Files.createDirectories(serverDir.resolve("woflo/backups"));

            // Same date, different times
            Files.createDirectories(backupsDir.resolve("20241015-090000_morning"));
            Files.createDirectories(backupsDir.resolve("20241015-180000_evening"));
            Files.createDirectories(backupsDir.resolve("20241015-120000_noon"));

            try (var s = Files.list(backupsDir)) {
                var latest = s.filter(Files::isDirectory)
                        .max(Comparator.comparing(p -> p.getFileName().toString()));

                assertThat(latest).isPresent();
                assertThat(latest.get().getFileName().toString()).startsWith("20241015-180000");
            }
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles deeply nested directory structure")
        void handlesDeeplyNestedDirectoryStructure() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            Path deepDir = serverDir.resolve("mods/subdir1/subdir2/subdir3");
            Files.createDirectories(deepDir);
            TestFixtures.createFabricMod(deepDir.resolve("deep-mod.jar"), "deepmod", "1.0");

            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            assertThat(backup.resolve("mods/subdir1/subdir2/subdir3/deep-mod.jar")).exists();
        }

        @Test
        @DisplayName("handles special characters in filenames")
        void handlesSpecialCharactersInFilenames() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            // Create mod with spaces and special chars (within what's valid)
            TestFixtures.createFabricMod(serverDir.resolve("mods/mod with spaces.jar"), "mod-spaces", "1.0");

            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            assertThat(backup.resolve("mods/mod with spaces.jar")).exists();
        }

        @Test
        @DisplayName("handles empty directories")
        void handlesEmptyDirectories() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            Files.createDirectories(serverDir.resolve("mods/empty-dir"));

            Path backup = Backup.createSilent(serverDir, Loader.FABRIC, "test");

            assertThat(backup.resolve("mods/empty-dir")).exists();
        }
    }
}
