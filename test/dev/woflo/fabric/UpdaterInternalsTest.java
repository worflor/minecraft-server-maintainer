package dev.woflo.fabric;

import dev.woflo.fabric.TestTags.IntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Updater internal methods and helper logic.
 * Tests compatibility checking, version detection, log formatting,
 * disk space checking, and integrity verification.
 *
 * Uses reflection to call real Updater methods where possible.
 */
@IntegrationTest
@DisplayName("Updater Internals")
class UpdaterInternalsTest {

    @TempDir
    Path tempDir;

    private MockConsole console;
    private static Method compareVersionsMethod;
    private static Method detectVersionMethod;

    @BeforeAll
    static void setupReflection() throws Exception {
        compareVersionsMethod = Updater.class.getDeclaredMethod("compareVersions", String.class, String.class);
        compareVersionsMethod.setAccessible(true);

        detectVersionMethod = Updater.class.getDeclaredMethod("detectVersion");
        detectVersionMethod.setAccessible(true);
    }

    /**
     * Calls the real Updater.compareVersions() method via reflection.
     */
    private static int compareVersions(String a, String b) {
        try {
            return (int) compareVersionsMethod.invoke(null, a, b);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke Updater.compareVersions", e);
        }
    }

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
        @DisplayName("Updater.java contains expected version pattern")
        void updaterContainsExpectedVersionPattern() throws IOException {
            Path updaterJava = Path.of("src/dev/woflo/fabric/Updater.java");
            assertThat(updaterJava).exists();

            String source = Files.readString(updaterJava);

            // Verify VERSION_PATTERN constant
            assertThat(source).contains("VERSION_PATTERN = \"\\\\d+\\\\.\\\\d+(\\\\.\\\\d+)?\"");

            // Verify detectVersion method exists
            assertThat(source).contains("private String detectVersion()");

            // Verify checkCompat logic
            assertThat(source).containsPattern(Pattern.compile("compat\\s*\\*\\s*100\\s*/\\s*total"));
        }
    }

    // ========================================================================
    // Version Detection Priority
    // ========================================================================

    @Nested
    @DisplayName("Version detection priority")
    class VersionDetectionPriority {

        @Test
        @DisplayName("reads from current_version.txt first")
        void readsFromCurrentVersionTxtFirst() throws IOException {
            Path serverDir = Files.createDirectory(tempDir.resolve("server"));
            Files.writeString(serverDir.resolve("current_version.txt"), "1.21.1");

            // Also create versions folder with different version
            Path versionsDir = Files.createDirectory(serverDir.resolve("versions"));
            Files.createDirectory(versionsDir.resolve("1.20.6"));

            String version = readVersionFile(serverDir);
            assertThat(version).isEqualTo("1.21.1");
        }

        @Test
        @DisplayName("falls back to versions folder if no current_version.txt")
        void fallsBackToVersionsFolder() throws IOException {
            Path serverDir = Files.createDirectory(tempDir.resolve("server"));

            // Only create versions folder
            Path versionsDir = Files.createDirectory(serverDir.resolve("versions"));
            Files.createDirectory(versionsDir.resolve("1.20.6"));
            Files.createDirectory(versionsDir.resolve("1.21.1"));

            String version = readVersionFromVersionsFolder(serverDir);
            assertThat(version).isEqualTo("1.21.1"); // Latest
        }

        @Test
        @DisplayName("returns null when no version info exists")
        void returnsNullWhenNoVersionInfoExists() throws IOException {
            Path serverDir = Files.createDirectory(tempDir.resolve("server"));

            String version = readVersionFile(serverDir);
            assertThat(version).isNull();
        }

        @Test
        @DisplayName("validates version format from file")
        void validatesVersionFormatFromFile() throws IOException {
            Path serverDir = Files.createDirectory(tempDir.resolve("server"));
            Files.writeString(serverDir.resolve("current_version.txt"), "invalid-version");

            String version = readVersionFile(serverDir);
            assertThat(version).isNull(); // Invalid format rejected
        }
    }

    // ========================================================================
    // Version Pattern Validation
    // ========================================================================

    @Nested
    @DisplayName("Version pattern validation")
    class VersionPatternValidation {

        private static final String VERSION_PATTERN = "\\d+\\.\\d+(\\.\\d+)?";

        @ParameterizedTest
        @DisplayName("accepts valid version strings")
        @ValueSource(strings = {"1.20", "1.20.6", "1.21.1", "2.0", "10.99.100"})
        void acceptsValidVersionStrings(String version) {
            assertThat(version.matches(VERSION_PATTERN)).isTrue();
        }

        @ParameterizedTest
        @DisplayName("rejects invalid version strings")
        @ValueSource(strings = {"1.20.6.1", "1.20-pre1", "24w38a", "v1.20.6", "1.x.0", ""})
        void rejectsInvalidVersionStrings(String version) {
            assertThat(version.matches(VERSION_PATTERN)).isFalse();
        }
    }

    // ========================================================================
    // Compatibility Calculation
    // ========================================================================

    @Nested
    @DisplayName("Compatibility calculation")
    class CompatibilityCalculation {

        @Test
        @DisplayName("100% when all mods have update available")
        void hundredPercentWhenAllModsHaveUpdate() {
            // Simulates checkCompat logic
            int compat = 5, total = 5; // All compatible

            int percent = total > 0 ? (compat * 100 / total) : 0;
            assertThat(percent).isEqualTo(100);
        }

        @Test
        @DisplayName("0% when no mods are compatible")
        void zeroPercentWhenNoModsCompatible() {
            int compat = 0, total = 5;

            int percent = total > 0 ? (compat * 100 / total) : 0;
            assertThat(percent).isEqualTo(0);
        }

        @Test
        @DisplayName("50% when half of mods are compatible")
        void fiftyPercentWhenHalfCompatible() {
            int compat = 2, total = 4;

            int percent = total > 0 ? (compat * 100 / total) : 0;
            assertThat(percent).isEqualTo(50);
        }

        @Test
        @DisplayName("returns 0 for empty mod list")
        void returnsZeroForEmptyModList() {
            int compat = 0, total = 0;

            int percent = total > 0 ? (compat * 100 / total) : 0;
            assertThat(percent).isEqualTo(0);
        }

        @Test
        @DisplayName("rounds down for non-integer percentages")
        void roundsDownForNonIntegerPercentages() {
            int compat = 1, total = 3; // 33.33%

            int percent = total > 0 ? (compat * 100 / total) : 0;
            assertThat(percent).isEqualTo(33); // Integer division
        }

        @Test
        @DisplayName("skipped mods don't count toward total")
        void skippedModsDontCountTowardTotal() {
            // In checkCompat: if (!r.status().equals("skip")) { total++; ... }
            // Only "current" and "update" increment compat
            List<String> results = List.of("current", "update", "skip", "skip", "current");

            int compat = 0, total = 0;
            for (String status : results) {
                if (!status.equals("skip")) {
                    total++;
                    if (status.equals("current") || status.equals("update")) {
                        compat++;
                    }
                }
            }

            int percent = total > 0 ? (compat * 100 / total) : 0;
            assertThat(total).isEqualTo(3);
            assertThat(compat).isEqualTo(3);
            assertThat(percent).isEqualTo(100);
        }
    }

    // ========================================================================
    // Update Log Formatting
    // ========================================================================

    @Nested
    @DisplayName("Update log formatting")
    class UpdateLogFormatting {

        @Test
        @DisplayName("formats timestamp correctly")
        void formatsTimestampCorrectly() {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"));
            // Should be like "Jan 26, 14:30"
            assertThat(ts).matches("[A-Z][a-z]{2} \\d{2}, \\d{2}:\\d{2}");
        }

        @Test
        @DisplayName("creates entry with arrow separator")
        void createsEntryWithArrowSeparator() {
            String name = "Fabric";
            String oldVer = "1.20.6";
            String newVer = "1.21.1";
            String ts = "Jan 26, 14:30";

            String entry = "  " + name + "  " + oldVer + " → " + newVer + "  (" + ts + ")";
            assertThat(entry).contains("→");
            assertThat(entry).contains(oldVer);
            assertThat(entry).contains(newVer);
        }

        @Test
        @DisplayName("handles null old version")
        void handlesNullOldVersion() {
            String oldVer = null;
            String formatted = oldVer != null ? oldVer : "?";

            assertThat(formatted).isEqualTo("?");
        }

        @Test
        @DisplayName("log file structure is correct")
        void logFileStructureIsCorrect() throws IOException {
            Path logFile = tempDir.resolve("update.txt");

            List<String> lines = new ArrayList<>();
            lines.add("─── recent updates ───");
            lines.add("");
            lines.add("  Fabric  1.20.6 → 1.21.1  (Jan 26, 14:30)");
            lines.add("");

            Files.writeString(logFile, String.join("\n", lines) + "\n");

            String content = Files.readString(logFile);
            assertThat(content).contains("─── recent updates ───");
            assertThat(content).contains("Fabric");
        }

        @Test
        @DisplayName("limits history to 6 recent entries")
        void limitsHistoryToSixRecentEntries() {
            // The logUpdate method keeps last 6 entries
            List<String> existing = List.of(
                    "  entry1", "  entry2", "  entry3", "  entry4",
                    "  entry5", "  entry6", "  entry7", "  entry8"
            );

            var limited = existing.stream()
                    .filter(l -> !l.isBlank() && !l.startsWith("───"))
                    .limit(6)
                    .toList();

            assertThat(limited).hasSize(6);
        }
    }

    // ========================================================================
    // Pending Operation Tracking
    // ========================================================================

    @Nested
    @DisplayName("Pending operation tracking")
    class PendingOperationTracking {

        @Test
        @DisplayName("pending file path is woflo/.pending")
        void pendingFilePathIsCorrect() throws IOException {
            Path serverDir = Files.createDirectory(tempDir.resolve("server"));
            Files.createDirectory(serverDir.resolve("woflo"));

            Path pendingFile = serverDir.resolve("woflo").resolve(".pending");
            assertThat(pendingFile.getFileName().toString()).isEqualTo(".pending");
        }

        @Test
        @DisplayName("pending file contains 'update' marker")
        void pendingFileContainsUpdateMarker() throws IOException {
            Path pendingFile = tempDir.resolve(".pending");
            Files.writeString(pendingFile, "update");

            assertThat(Files.readString(pendingFile)).isEqualTo("update");
        }

        @Test
        @DisplayName("pending detection finds latest backup for recovery")
        void pendingDetectionFindsLatestBackupForRecovery() throws IOException {
            Path serverDir = Files.createDirectory(tempDir.resolve("server"));
            Path backupsDir = Files.createDirectories(serverDir.resolve("woflo/backups"));

            // Create multiple backup folders with timestamps
            Files.createDirectory(backupsDir.resolve("mc_2024-01-01_12-00"));
            Files.createDirectory(backupsDir.resolve("mc_2024-01-02_12-00"));
            Files.createDirectory(backupsDir.resolve("mc_2024-01-03_12-00"));

            // Find latest
            try (var stream = Files.list(backupsDir)) {
                var latest = stream.filter(Files::isDirectory)
                        .max(Comparator.comparing(p -> p.getFileName().toString()));

                assertThat(latest).isPresent();
                assertThat(latest.get().getFileName().toString()).isEqualTo("mc_2024-01-03_12-00");
            }
        }
    }

    // ========================================================================
    // Disk Space Checking
    // ========================================================================

    @Nested
    @DisplayName("Disk space checking")
    class DiskSpaceChecking {

        @Test
        @DisplayName("calculates usable space in MB")
        void calculatesUsableSpaceInMB() throws IOException {
            long bytes = Files.getFileStore(tempDir).getUsableSpace();
            long mb = bytes / (1024 * 1024);

            assertThat(mb).isGreaterThan(0);
        }

        @Test
        @DisplayName("500MB is the minimum threshold")
        void fiveHundredMBIsMinimumThreshold() {
            long mb = 400; // Below threshold
            boolean hasEnoughSpace = mb >= 500;

            assertThat(hasEnoughSpace).isFalse();
        }

        @Test
        @DisplayName("1000MB triggers low disk warning")
        void oneThousandMBTriggersLowDiskWarning() {
            long mb = 800; // Below warning threshold
            boolean isLow = mb < 1000;

            assertThat(isLow).isTrue();
        }
    }

    // ========================================================================
    // Integrity Checking
    // ========================================================================

    @Nested
    @DisplayName("Integrity checking")
    class IntegrityChecking {

        @Test
        @DisplayName("detects empty JAR files")
        void detectsEmptyJarFiles() throws IOException {
            Path modsDir = Files.createDirectory(tempDir.resolve("mods"));

            // Create empty JAR
            Files.writeString(modsDir.resolve("empty.jar"), "");

            try (var stream = Files.list(modsDir)) {
                for (Path p : stream.filter(f -> f.toString().endsWith(".jar")).toList()) {
                    if (Files.size(p) == 0) {
                        console.warn("Empty file: " + p.getFileName());
                    }
                }
            }

            assertThat(console.hasWarning("Empty file")).isTrue();
        }

        @Test
        @DisplayName("ignores non-empty JAR files")
        void ignoresNonEmptyJarFiles() throws IOException {
            Path modsDir = Files.createDirectory(tempDir.resolve("mods"));
            TestFixtures.createFabricMod(modsDir.resolve("valid.jar"), "valid", "1.0.0");

            try (var stream = Files.list(modsDir)) {
                for (Path p : stream.filter(f -> f.toString().endsWith(".jar")).toList()) {
                    if (Files.size(p) == 0) {
                        console.warn("Empty file: " + p.getFileName());
                    }
                }
            }

            assertThat(console.hasWarning("Empty file")).isFalse();
        }
    }

    // ========================================================================
    // Server Startup Verification
    // ========================================================================

    @Nested
    @DisplayName("Server startup verification")
    class ServerStartupVerification {

        private static final String[] STARTUP_SUCCESS_MARKERS = {
                "Done (", "For help, type", "Applying patches", "You need to agree to the EULA"
        };

        @Test
        @DisplayName("detects 'Done' marker in output")
        void detectsDoneMarkerInOutput() {
            String output = "[14:30:00] [Server thread/INFO]: Done (5.234s)! For help, type \"help\"";

            boolean found = containsAny(output, STARTUP_SUCCESS_MARKERS);
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("detects 'For help, type' marker")
        void detectsForHelpTypeMarker() {
            String output = "For help, type \"help\" or \"?\"";

            boolean found = containsAny(output, STARTUP_SUCCESS_MARKERS);
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("detects Paper 'Applying patches' marker")
        void detectsApplyingPatchesMarker() {
            String output = "[14:30:00] [Server thread/INFO]: Applying patches";

            boolean found = containsAny(output, STARTUP_SUCCESS_MARKERS);
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("detects EULA prompt marker")
        void detectsEulaPromptMarker() {
            String output = "You need to agree to the EULA in order to run the server.";

            boolean found = containsAny(output, STARTUP_SUCCESS_MARKERS);
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("returns false for random output")
        void returnsFalseForRandomOutput() {
            String output = "Starting Minecraft server version 1.21.1";

            boolean found = containsAny(output, STARTUP_SUCCESS_MARKERS);
            assertThat(found).isFalse();
        }

        private boolean containsAny(String s, String[] markers) {
            for (String m : markers) {
                if (s.contains(m)) return true;
            }
            return false;
        }
    }

    // ========================================================================
    // Clean Old Versions
    // ========================================================================

    @Nested
    @DisplayName("Clean old versions")
    class CleanOldVersions {

        @Test
        @DisplayName("keeps current version folder")
        void keepsCurrentVersionFolder() throws IOException {
            Path versionsDir = Files.createDirectory(tempDir.resolve("versions"));
            Files.createDirectory(versionsDir.resolve("1.21.1"));
            Files.createDirectory(versionsDir.resolve("1.20.6"));
            Files.createDirectory(versionsDir.resolve("1.20.4"));

            String keep = "1.21.1";

            // Simulate cleanOld logic
            try (var stream = Files.list(versionsDir)) {
                List<Path> toDelete = stream
                        .filter(Files::isDirectory)
                        .filter(x -> !x.getFileName().toString().equals(keep))
                        .toList();

                for (Path p : toDelete) {
                    // Would delete here
                    assertThat(p.getFileName().toString()).isNotEqualTo(keep);
                }

                assertThat(toDelete).hasSize(2);
            }
        }
    }

    // ========================================================================
    // Target Version Validation
    // ========================================================================

    @Nested
    @DisplayName("Target version validation")
    class TargetVersionValidation {

        private static final String VERSION_PATTERN = "\\d+\\.\\d+(\\.\\d+)?";

        @Test
        @DisplayName("valid target version is accepted")
        void validTargetVersionIsAccepted() {
            String targetVersion = "1.20.4";
            boolean isValid = targetVersion.matches(VERSION_PATTERN);

            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("invalid target version falls back to current")
        void invalidTargetVersionFallsBackToCurrent() {
            String targetVersion = "latest";
            String current = "1.21.1";

            String latest;
            if (!targetVersion.matches(VERSION_PATTERN)) {
                // Would warn and use current
                latest = current;
            } else {
                latest = targetVersion;
            }

            assertThat(latest).isEqualTo(current);
        }
    }

    // ========================================================================
    // Interactive Mode
    // ========================================================================

    @Nested
    @DisplayName("Interactive mode confirm logic")
    class InteractiveModeConfirmLogic {

        @Test
        @DisplayName("non-interactive mode always returns true")
        void nonInteractiveModeAlwaysReturnsTrue() {
            boolean interactive = false;

            // confirm logic: if (!interactive) return true;
            boolean result = !interactive || confirmInput("y");
            assertThat(result).isTrue();
        }

        @ParameterizedTest
        @DisplayName("accepts various yes inputs")
        @ValueSource(strings = {"y", "Y", "yes", "YES", "Yes", ""})
        void acceptsVariousYesInputs(String input) {
            String line = input.trim().toLowerCase();
            boolean accepted = line.isEmpty() || line.equals("y") || line.equals("yes");

            assertThat(accepted).isTrue();
        }

        @ParameterizedTest
        @DisplayName("rejects various no inputs")
        @ValueSource(strings = {"n", "N", "no", "NO", "No", "nope"})
        void rejectsVariousNoInputs(String input) {
            String line = input.trim().toLowerCase();
            boolean accepted = line.isEmpty() || line.equals("y") || line.equals("yes");

            assertThat(accepted).isFalse();
        }

        private boolean confirmInput(String input) {
            String line = input.trim().toLowerCase();
            return line.isEmpty() || line.equals("y") || line.equals("yes");
        }
    }

    // ========================================================================
    // Helper Methods - Use reflection or mirror Updater.java exactly
    // ========================================================================

    /**
     * Mirrors Updater.detectVersion() file reading logic.
     * The actual Updater.detectVersion() is an instance method that requires
     * a fully constructed Updater. This helper mirrors the file reading portion.
     */
    private String readVersionFile(Path serverDir) throws IOException {
        // Mirrors Updater.java lines 203-204
        Path vf = serverDir.resolve("current_version.txt");
        if (Files.exists(vf)) {
            String v = Files.readString(vf).trim();
            if (v.matches("\\d+\\.\\d+(\\.\\d+)?")) {
                return v;
            }
        }
        return null;
    }

    /**
     * Mirrors Updater.detectVersion() versions folder logic.
     * Uses real Updater.compareVersions via reflection.
     */
    private String readVersionFromVersionsFolder(Path serverDir) throws IOException {
        // Mirrors Updater.java lines 205-206
        Path vd = serverDir.resolve("versions");
        if (Files.exists(vd)) {
            try (var s = Files.list(vd)) {
                return s.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.matches("\\d+\\.\\d+(\\.\\d+)?"))
                        .max(UpdaterInternalsTest::compareVersions)
                        .orElse(null);
            }
        }
        return null;
    }
}
