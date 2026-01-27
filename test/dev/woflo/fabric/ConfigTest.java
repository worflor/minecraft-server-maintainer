package dev.woflo.fabric;

import dev.woflo.fabric.Config;
import dev.woflo.fabric.TestTags.UnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for Config.java.
 * Tests YAML parsing, config loading, generation, and all configuration options.
 */
@UnitTest
@DisplayName("Config")
class ConfigTest {

    @TempDir
    Path tempDir;

    // ========================================================================
    // Default Values
    // ========================================================================

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("creates config with default memory settings")
        void defaultMemorySettings() throws IOException {
            var config = Config.load(tempDir);

            assertThat(config.memoryMin).isEqualTo("2G");
            assertThat(config.memoryMax).isEqualTo("6G");
        }

        @Test
        @DisplayName("creates config with default JVM args")
        void defaultJvmArgs() throws IOException {
            var config = Config.load(tempDir);

            assertThat(config.jvmArgs)
                    .contains("-Djava.awt.headless=true")
                    .contains("-XX:+UseG1GC")
                    .contains("-XX:+ParallelRefProcEnabled")
                    .contains("-XX:+DisableExplicitGC")
                    .contains("-XX:+AlwaysPreTouch");
        }

        @Test
        @DisplayName("creates config with default update settings")
        void defaultUpdateSettings() throws IOException {
            var config = Config.load(tempDir);

            assertThat(config.updateMinecraft).isTrue();
            assertThat(config.updateMods).isTrue();
            assertThat(config.updatePlugins).isTrue();
            assertThat(config.updateDatapacks).isFalse();
            assertThat(config.minCompatibility).isEqualTo(90);
            assertThat(config.allowSnapshots).isFalse();
            assertThat(config.allowBeta).isFalse();
        }

        @Test
        @DisplayName("creates config with default behavior settings")
        void defaultBehaviorSettings() throws IOException {
            var config = Config.load(tempDir);

            assertThat(config.startupTimeout).isEqualTo(90);
            assertThat(config.interactive).isFalse();
            assertThat(config.restartDelay).isEqualTo(5);
            assertThat(config.maxCrashes).isEqualTo(3);
            assertThat(config.crashWindow).isEqualTo(300);
        }

        @Test
        @DisplayName("creates config with default stasis settings")
        void defaultStasisSettings() throws IOException {
            var config = Config.load(tempDir);

            assertThat(config.stasisEnabled).isFalse();
            assertThat(config.stasisInterval).isEqualTo(24);
            assertThat(config.stasisKeep).isEqualTo(3);
        }

        @Test
        @DisplayName("creates config with null optional settings")
        void defaultOptionalSettings() throws IOException {
            var config = Config.load(tempDir);

            assertThat(config.targetVersion).isNull();
            assertThat(config.serverJar).isNull();
        }

        @Test
        @DisplayName("BACKUP_KEEP_DAYS constant is 7")
        void backupKeepDaysConstant() {
            assertThat(Config.BACKUP_KEEP_DAYS).isEqualTo(7);
        }
    }

    // ========================================================================
    // Config File Creation
    // ========================================================================

    @Nested
    @DisplayName("Config file creation")
    class ConfigFileCreation {

        @Test
        @DisplayName("creates woflo directory if not exists")
        void createsWofloDirectory() throws IOException {
            Config.load(tempDir);

            assertThat(tempDir.resolve("woflo")).exists().isDirectory();
        }

        @Test
        @DisplayName("creates config.yml if not exists")
        void createsConfigFile() throws IOException {
            Config.load(tempDir);

            assertThat(tempDir.resolve("woflo/config.yml")).exists().isRegularFile();
        }

        @Test
        @DisplayName("generated config contains version header")
        void generatedConfigContainsVersionHeader() throws IOException {
            Config.load(tempDir);

            String content = Files.readString(tempDir.resolve("woflo/config.yml"));
            assertThat(content).contains("Server Maintainer by woflo");
            assertThat(content).contains("[ v ");
        }

        @Test
        @DisplayName("generated config contains all sections")
        void generatedConfigContainsAllSections() throws IOException {
            Config.load(tempDir);

            String content = Files.readString(tempDir.resolve("woflo/config.yml"));
            assertThat(content)
                    .contains("memory:")
                    .contains("jvm-args:")
                    .contains("updates:")
                    .contains("startup-timeout:")
                    .contains("interactive:")
                    .contains("restart-delay:")
                    .contains("stasis:");
        }

        @Test
        @DisplayName("does not rewrite unchanged config")
        void doesNotRewriteUnchangedConfig() throws IOException {
            Config.load(tempDir);
            Path configPath = tempDir.resolve("woflo/config.yml");
            long modTimeFirst = Files.getLastModifiedTime(configPath).toMillis();

            // Small delay to ensure different timestamp if rewritten
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}

            Config.load(tempDir);
            long modTimeSecond = Files.getLastModifiedTime(configPath).toMillis();

            // File should not be rewritten if content unchanged
            assertThat(modTimeFirst).isEqualTo(modTimeSecond);
        }
    }

    // ========================================================================
    // Parsing Memory Settings
    // ========================================================================

    @Nested
    @DisplayName("Memory settings parsing")
    class MemorySettingsParsing {

        @Test
        @DisplayName("parses custom min memory")
        void parsesCustomMinMemory() throws IOException {
            writeConfig("""
                    memory:
                      min: 4G
                      max: 8G
                    """);

            var config = Config.load(tempDir);
            assertThat(config.memoryMin).isEqualTo("4G");
        }

        @Test
        @DisplayName("parses custom max memory")
        void parsesCustomMaxMemory() throws IOException {
            writeConfig("""
                    memory:
                      min: 4G
                      max: 16G
                    """);

            var config = Config.load(tempDir);
            assertThat(config.memoryMax).isEqualTo("16G");
        }

        @ParameterizedTest
        @DisplayName("parses various memory formats")
        @CsvSource({
                "512M, 512M",
                "1G, 1G",
                "4G, 4G",
                "16G, 16G",
                "32768M, 32768M"
        })
        void parsesVariousMemoryFormats(String input, String expected) throws IOException {
            writeConfig("""
                    memory:
                      min: %s
                      max: %s
                    """.formatted(input, input));

            var config = Config.load(tempDir);
            assertThat(config.memoryMin).isEqualTo(expected);
            assertThat(config.memoryMax).isEqualTo(expected);
        }
    }

    // ========================================================================
    // Parsing JVM Args
    // ========================================================================

    @Nested
    @DisplayName("JVM args parsing")
    class JvmArgsParsing {

        @Test
        @DisplayName("parses custom JVM args")
        void parsesCustomJvmArgs() throws IOException {
            writeConfig("""
                    jvm-args:
                      - -Xss1M
                      - -XX:+UseZGC
                    """);

            var config = Config.load(tempDir);
            assertThat(config.jvmArgs)
                    .containsExactly("-Xss1M", "-XX:+UseZGC");
        }

        @Test
        @DisplayName("clears default JVM args when custom provided")
        void clearsDefaultJvmArgsWhenCustomProvided() throws IOException {
            writeConfig("""
                    jvm-args:
                      - -XX:+UseZGC
                    """);

            var config = Config.load(tempDir);
            assertThat(config.jvmArgs)
                    .containsExactly("-XX:+UseZGC")
                    .doesNotContain("-XX:+UseG1GC");
        }

        @Test
        @DisplayName("handles empty JVM args list")
        void handlesEmptyJvmArgsList() throws IOException {
            writeConfig("""
                    jvm-args:
                    updates:
                      minecraft: true
                    """);

            var config = Config.load(tempDir);
            // With no list items, defaults should remain
            assertThat(config.jvmArgs).isNotEmpty();
        }

        @Test
        @DisplayName("preserves JVM arg ordering")
        void preservesJvmArgOrdering() throws IOException {
            writeConfig("""
                    jvm-args:
                      - -Xss1M
                      - -XX:+UseZGC
                      - -XX:MaxGCPauseMillis=200
                    """);

            var config = Config.load(tempDir);
            assertThat(config.jvmArgs).containsExactly(
                    "-Xss1M",
                    "-XX:+UseZGC",
                    "-XX:MaxGCPauseMillis=200"
            );
        }
    }

    // ========================================================================
    // Parsing Update Settings
    // ========================================================================

    @Nested
    @DisplayName("Update settings parsing")
    class UpdateSettingsParsing {

        @Test
        @DisplayName("parses minecraft update setting")
        void parsesMinecraftUpdateSetting() throws IOException {
            writeConfig("""
                    updates:
                      minecraft: false
                    """);

            var config = Config.load(tempDir);
            assertThat(config.updateMinecraft).isFalse();
        }

        @Test
        @DisplayName("parses mods update setting")
        void parsesModsUpdateSetting() throws IOException {
            writeConfig("""
                    updates:
                      mods: false
                    """);

            var config = Config.load(tempDir);
            assertThat(config.updateMods).isFalse();
        }

        @Test
        @DisplayName("parses plugins update setting")
        void parsesPluginsUpdateSetting() throws IOException {
            writeConfig("""
                    updates:
                      plugins: false
                    """);

            var config = Config.load(tempDir);
            assertThat(config.updatePlugins).isFalse();
        }

        @Test
        @DisplayName("parses datapacks update setting")
        void parsesDatapacksUpdateSetting() throws IOException {
            writeConfig("""
                    updates:
                      datapacks: true
                    """);

            var config = Config.load(tempDir);
            assertThat(config.updateDatapacks).isTrue();
        }

        @Test
        @DisplayName("parses min-compatibility setting")
        void parsesMinCompatibilitySetting() throws IOException {
            writeConfig("""
                    updates:
                      min-compatibility: 75
                    """);

            var config = Config.load(tempDir);
            assertThat(config.minCompatibility).isEqualTo(75);
        }

        @Test
        @DisplayName("parses allow-snapshots setting")
        void parsesAllowSnapshotsSetting() throws IOException {
            writeConfig("""
                    updates:
                      allow-snapshots: true
                    """);

            var config = Config.load(tempDir);
            assertThat(config.allowSnapshots).isTrue();
        }

        @Test
        @DisplayName("parses allow-beta setting")
        void parsesAllowBetaSetting() throws IOException {
            writeConfig("""
                    updates:
                      allow-beta: true
                    """);

            var config = Config.load(tempDir);
            assertThat(config.allowBeta).isTrue();
        }
    }

    // ========================================================================
    // Parsing Boolean Values
    // ========================================================================

    @Nested
    @DisplayName("Boolean parsing")
    class BooleanParsing {

        @ParameterizedTest
        @DisplayName("parses true values")
        @ValueSource(strings = {"true", "TRUE", "True", "yes", "YES", "Yes"})
        void parsesTrueValues(String value) throws IOException {
            writeConfig("interactive: " + value);

            var config = Config.load(tempDir);
            assertThat(config.interactive).isTrue();
        }

        @ParameterizedTest
        @DisplayName("parses false values")
        @ValueSource(strings = {"false", "FALSE", "False", "no", "NO", "No", "anything", ""})
        void parsesFalseValues(String value) throws IOException {
            writeConfig("interactive: " + value);

            var config = Config.load(tempDir);
            assertThat(config.interactive).isFalse();
        }
    }

    // ========================================================================
    // Parsing Integer Values
    // ========================================================================

    @Nested
    @DisplayName("Integer parsing")
    class IntegerParsing {

        @Test
        @DisplayName("parses valid integer")
        void parsesValidInteger() throws IOException {
            writeConfig("startup-timeout: 120");

            var config = Config.load(tempDir);
            assertThat(config.startupTimeout).isEqualTo(120);
        }

        @Test
        @DisplayName("defaults on invalid integer")
        void defaultsOnInvalidInteger() throws IOException {
            writeConfig("startup-timeout: invalid");

            var config = Config.load(tempDir);
            assertThat(config.startupTimeout).isEqualTo(90); // default
        }

        @Test
        @DisplayName("defaults on empty integer")
        void defaultsOnEmptyInteger() throws IOException {
            writeConfig("startup-timeout: ");

            var config = Config.load(tempDir);
            assertThat(config.startupTimeout).isEqualTo(90); // default
        }

        @ParameterizedTest
        @DisplayName("parses various integer settings")
        @CsvSource({
                "restart-delay, 10, restartDelay, 10",
                "max-crashes, 5, maxCrashes, 5",
                "crash-window, 600, crashWindow, 600"
        })
        void parsesVariousIntegerSettings(String key, String value, String field, int expected) throws IOException {
            writeConfig(key + ": " + value);

            var config = Config.load(tempDir);
            int actual = switch (field) {
                case "restartDelay" -> config.restartDelay;
                case "maxCrashes" -> config.maxCrashes;
                case "crashWindow" -> config.crashWindow;
                default -> throw new IllegalArgumentException("Unknown field: " + field);
            };
            assertThat(actual).isEqualTo(expected);
        }
    }

    // ========================================================================
    // Parsing Stasis Settings
    // ========================================================================

    @Nested
    @DisplayName("Stasis settings parsing")
    class StasisSettingsParsing {

        @Test
        @DisplayName("parses stasis enabled")
        void parsesStasisEnabled() throws IOException {
            writeConfig("""
                    stasis:
                      enabled: true
                    """);

            var config = Config.load(tempDir);
            assertThat(config.stasisEnabled).isTrue();
        }

        @Test
        @DisplayName("parses stasis interval")
        void parsesStasisInterval() throws IOException {
            writeConfig("""
                    stasis:
                      interval: 12
                    """);

            var config = Config.load(tempDir);
            assertThat(config.stasisInterval).isEqualTo(12);
        }

        @Test
        @DisplayName("parses stasis keep count")
        void parsesStasisKeepCount() throws IOException {
            writeConfig("""
                    stasis:
                      keep: 5
                    """);

            var config = Config.load(tempDir);
            assertThat(config.stasisKeep).isEqualTo(5);
        }
    }

    // ========================================================================
    // Parsing Optional Settings
    // ========================================================================

    @Nested
    @DisplayName("Optional settings parsing")
    class OptionalSettingsParsing {

        @Test
        @DisplayName("parses target-version")
        void parsesTargetVersion() throws IOException {
            writeConfig("target-version: 1.20.4");

            var config = Config.load(tempDir);
            assertThat(config.targetVersion).isEqualTo("1.20.4");
        }

        @Test
        @DisplayName("parses server-jar")
        void parsesServerJar() throws IOException {
            writeConfig("server-jar: custom-server.jar");

            var config = Config.load(tempDir);
            assertThat(config.serverJar).isEqualTo("custom-server.jar");
        }

        @Test
        @DisplayName("handles quoted values")
        void handlesQuotedValues() throws IOException {
            writeConfig("target-version: \"1.20.4\"");

            var config = Config.load(tempDir);
            assertThat(config.targetVersion).isEqualTo("1.20.4");
        }
    }

    // ========================================================================
    // Comment Handling
    // ========================================================================

    @Nested
    @DisplayName("Comment handling")
    class CommentHandling {

        @Test
        @DisplayName("ignores comment lines")
        void ignoresCommentLines() throws IOException {
            writeConfig("""
                    # This is a comment
                    interactive: true
                    # Another comment
                    """);

            var config = Config.load(tempDir);
            assertThat(config.interactive).isTrue();
        }

        @Test
        @DisplayName("ignores inline comments")
        void ignoresInlineComments() throws IOException {
            writeConfig("startup-timeout: 120 # custom timeout");

            var config = Config.load(tempDir);
            assertThat(config.startupTimeout).isEqualTo(120);
        }

        @Test
        @DisplayName("ignores empty lines")
        void ignoresEmptyLines() throws IOException {
            writeConfig("""

                    interactive: true

                    startup-timeout: 120

                    """);

            var config = Config.load(tempDir);
            assertThat(config.interactive).isTrue();
            assertThat(config.startupTimeout).isEqualTo(120);
        }
    }

    // ========================================================================
    // Config Regeneration
    // ========================================================================

    @Nested
    @DisplayName("Config regeneration")
    class ConfigRegeneration {

        @Test
        @DisplayName("regenerates config preserving custom values")
        void regeneratesConfigPreservingCustomValues() throws IOException {
            writeConfig("""
                    memory:
                      min: 4G
                      max: 12G
                    interactive: true
                    startup-timeout: 120
                    """);

            var config = Config.load(tempDir);

            // Verify values were preserved
            assertThat(config.memoryMin).isEqualTo("4G");
            assertThat(config.memoryMax).isEqualTo("12G");
            assertThat(config.interactive).isTrue();
            assertThat(config.startupTimeout).isEqualTo(120);

            // Verify regenerated file contains custom values
            String content = Files.readString(tempDir.resolve("woflo/config.yml"));
            assertThat(content).contains("min: 4G");
            assertThat(content).contains("max: 12G");
            assertThat(content).contains("interactive: true");
            assertThat(content).contains("startup-timeout: 120");
        }

        @Test
        @DisplayName("uncomments target-version when set")
        void uncommentsTargetVersionWhenSet() throws IOException {
            writeConfig("target-version: 1.20.4");

            Config.load(tempDir);

            String content = Files.readString(tempDir.resolve("woflo/config.yml"));
            assertThat(content).contains("target-version: 1.20.4");
            assertThat(content).doesNotContain("# target-version: 1.20.4");
        }

        @Test
        @DisplayName("uncomments server-jar when set")
        void uncommentsServerJarWhenSet() throws IOException {
            writeConfig("server-jar: paper.jar");

            Config.load(tempDir);

            String content = Files.readString(tempDir.resolve("woflo/config.yml"));
            assertThat(content).contains("server-jar: paper.jar");
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles malformed config gracefully")
        void handlesMalformedConfigGracefully() throws IOException {
            writeConfig("this is not valid yaml : : :");

            // Should not throw, should use defaults
            var config = Config.load(tempDir);
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("handles config with only comments")
        void handlesConfigWithOnlyComments() throws IOException {
            writeConfig("""
                    # Just comments
                    # Nothing else
                    """);

            var config = Config.load(tempDir);
            assertThat(config.memoryMin).isEqualTo("2G"); // default
        }

        @Test
        @DisplayName("handles multiple colons in value")
        void handlesMultipleColonsInValue() throws IOException {
            // The parser takes everything after first colon as value
            writeConfig("server-jar: my:custom:server.jar");

            var config = Config.load(tempDir);
            assertThat(config.serverJar).isEqualTo("my:custom:server.jar");
        }

        @Test
        @DisplayName("handles nested section with wrong indentation")
        void handlesNestedSectionWithWrongIndentation() throws IOException {
            writeConfig("""
                    memory:
                    min: 4G
                    max: 8G
                    """);

            // Without proper indentation, these are top-level keys
            var config = Config.load(tempDir);
            // Default values should be used since parsing won't work correctly
            assertThat(config).isNotNull();
        }

        @Test
        @DisplayName("handles very long values")
        void handlesVeryLongValues() throws IOException {
            String longJvmArg = "-D" + "x".repeat(1000) + "=value";
            writeConfig("""
                    jvm-args:
                      - %s
                    """.formatted(longJvmArg));

            var config = Config.load(tempDir);
            assertThat(config.jvmArgs).contains(longJvmArg);
        }
    }

    // ========================================================================
    // Complete Config Test
    // ========================================================================

    @Test
    @DisplayName("loads complete custom config")
    void loadsCompleteCustomConfig() throws IOException {
        writeConfig("""
                # Custom test config
                memory:
                  min: 4G
                  max: 16G

                jvm-args:
                  - -XX:+UseZGC
                  - -XX:MaxGCPauseMillis=50

                updates:
                  minecraft: true
                  mods: true
                  plugins: false
                  datapacks: true
                  min-compatibility: 80
                  allow-snapshots: true
                  allow-beta: true

                startup-timeout: 180
                interactive: true
                restart-delay: 10
                max-crashes: 5
                crash-window: 600

                target-version: 1.21.1
                server-jar: paper.jar

                stasis:
                  enabled: true
                  interval: 12
                  keep: 5
                """);

        var config = Config.load(tempDir);

        // Memory
        assertThat(config.memoryMin).isEqualTo("4G");
        assertThat(config.memoryMax).isEqualTo("16G");

        // JVM args
        assertThat(config.jvmArgs).containsExactly("-XX:+UseZGC", "-XX:MaxGCPauseMillis=50");

        // Updates
        assertThat(config.updateMinecraft).isTrue();
        assertThat(config.updateMods).isTrue();
        assertThat(config.updatePlugins).isFalse();
        assertThat(config.updateDatapacks).isTrue();
        assertThat(config.minCompatibility).isEqualTo(80);
        assertThat(config.allowSnapshots).isTrue();
        assertThat(config.allowBeta).isTrue();

        // Behavior
        assertThat(config.startupTimeout).isEqualTo(180);
        assertThat(config.interactive).isTrue();
        assertThat(config.restartDelay).isEqualTo(10);
        assertThat(config.maxCrashes).isEqualTo(5);
        assertThat(config.crashWindow).isEqualTo(600);

        // Optional
        assertThat(config.targetVersion).isEqualTo("1.21.1");
        assertThat(config.serverJar).isEqualTo("paper.jar");

        // Stasis
        assertThat(config.stasisEnabled).isTrue();
        assertThat(config.stasisInterval).isEqualTo(12);
        assertThat(config.stasisKeep).isEqualTo(5);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private void writeConfig(String content) throws IOException {
        Path configPath = tempDir.resolve("woflo/config.yml");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, content);
    }
}
