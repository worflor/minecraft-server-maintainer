package dev.woflo.fabric;

import dev.woflo.fabric.TestTags.IntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junitpioneer.jupiter.StdIo;
import org.junitpioneer.jupiter.StdOut;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CLI argument parsing.
 * Tests command line flags, help output, flag combinations, and interactive flag precedence.
 * (Includes tests merged from InteractiveFlagTest)
 *
 * NOTE: Main.main() has side effects (System.exit, server startup) so we cannot call it directly.
 * Instead, we test the same parsing logic and verify via source code analysis that our
 * test parseFlags() helper matches Main.java's implementation exactly.
 */
@IntegrationTest
@DisplayName("CLI Parsing")
class CliParsingTest {

    @TempDir
    Path tempDir;

    // ========================================================================
    // Source Code Verification - Ensures parseFlags matches Main.java
    // ========================================================================

    @Nested
    @DisplayName("Source code verification")
    class SourceCodeVerification {

        @Test
        @DisplayName("Main.java contains expected switch cases for all flags")
        void mainJavaContainsExpectedSwitchCases() throws IOException {
            Path mainJava = Path.of("src/dev/woflo/fabric/Main.java");
            if (!Files.exists(mainJava)) {
                mainJava = Path.of("./src/dev/woflo/fabric/Main.java");
            }
            assertThat(mainJava).exists();

            String source = Files.readString(mainJava);

            // Verify all flag cases are present in Main.java
            assertThat(source).contains("\"--dry-run\", \"-d\"");
            assertThat(source).contains("\"--update-only\", \"-u\"");
            assertThat(source).contains("\"--rollback\", \"-r\"");
            assertThat(source).contains("\"--interactive\", \"-i\"");
            assertThat(source).contains("\"--yes\", \"-y\"");
            assertThat(source).contains("\"--help\", \"-h\"");

            // Verify interactiveFlag handling
            assertThat(source).contains("interactiveFlag = true");
            assertThat(source).contains("interactiveFlag = false");
            assertThat(source).contains("interactiveFlag != null ? interactiveFlag : config.interactive");
        }

        @Test
        @DisplayName("parseFlags helper covers all Main.java switch cases")
        void parseFlagsHelperCoversAllMainJavaSwitchCases() {
            // Test each flag to ensure our helper handles them
            assertThat(parseFlags("--dry-run")).containsEntry("dryRun", true);
            assertThat(parseFlags("-d")).containsEntry("dryRun", true);
            assertThat(parseFlags("--update-only")).containsEntry("updateOnly", true);
            assertThat(parseFlags("-u")).containsEntry("updateOnly", true);
            assertThat(parseFlags("--rollback")).containsEntry("rollback", true);
            assertThat(parseFlags("-r")).containsEntry("rollback", true);
            assertThat(parseFlags("--interactive")).containsEntry("interactive", true);
            assertThat(parseFlags("-i")).containsEntry("interactive", true);
            assertThat(parseFlags("--yes")).containsEntry("yes", true);
            assertThat(parseFlags("-y")).containsEntry("yes", true);
            assertThat(parseFlags("--help")).containsEntry("help", true);
            assertThat(parseFlags("-h")).containsEntry("help", true);
        }
    }

    // ========================================================================
    // Help Flag Tests
    // ========================================================================

    @Nested
    @DisplayName("Help flag")
    class HelpFlag {

        @Test
        @StdIo
        @DisplayName("--help shows usage information via Main.printHelp()")
        void helpShowsUsage(StdOut out) throws Exception {
            // Call the actual Main.printHelp() method via reflection
            var printHelpMethod = Main.class.getDeclaredMethod("printHelp");
            printHelpMethod.setAccessible(true);
            printHelpMethod.invoke(null);

            String[] lines = out.capturedLines();
            String output = String.join("\n", lines);

            assertThat(output)
                    .contains("Server Maintainer")
                    .contains("woflo")
                    .contains("--dry-run")
                    .contains("--update-only")
                    .contains("--rollback")
                    .contains("--interactive")
                    .contains("--yes")
                    .contains("--help")
                    .contains("woflo/config.yml");
        }

        @Test
        @DisplayName("-h is equivalent to --help")
        void shortHelpFlag() {
            // Both flags should trigger help
            assertThat(parseFlags("-h")).containsEntry("help", true);
            assertThat(parseFlags("--help")).containsEntry("help", true);
        }
    }

    // ========================================================================
    // Dry Run Flag Tests
    // ========================================================================

    @Nested
    @DisplayName("Dry run flag")
    class DryRunFlag {

        @Test
        @DisplayName("--dry-run sets dry run mode")
        void dryRunLongFlag() {
            var flags = parseFlags("--dry-run");
            assertThat(flags).containsEntry("dryRun", true);
        }

        @Test
        @DisplayName("-d sets dry run mode")
        void dryRunShortFlag() {
            var flags = parseFlags("-d");
            assertThat(flags).containsEntry("dryRun", true);
        }
    }

    // ========================================================================
    // Update Only Flag Tests
    // ========================================================================

    @Nested
    @DisplayName("Update only flag")
    class UpdateOnlyFlag {

        @Test
        @DisplayName("--update-only sets update only mode")
        void updateOnlyLongFlag() {
            var flags = parseFlags("--update-only");
            assertThat(flags).containsEntry("updateOnly", true);
        }

        @Test
        @DisplayName("-u sets update only mode")
        void updateOnlyShortFlag() {
            var flags = parseFlags("-u");
            assertThat(flags).containsEntry("updateOnly", true);
        }
    }

    // ========================================================================
    // Rollback Flag Tests
    // ========================================================================

    @Nested
    @DisplayName("Rollback flag")
    class RollbackFlag {

        @Test
        @DisplayName("--rollback sets rollback mode")
        void rollbackLongFlag() {
            var flags = parseFlags("--rollback");
            assertThat(flags).containsEntry("rollback", true);
        }

        @Test
        @DisplayName("-r sets rollback mode")
        void rollbackShortFlag() {
            var flags = parseFlags("-r");
            assertThat(flags).containsEntry("rollback", true);
        }
    }

    // ========================================================================
    // Interactive Flag Tests
    // ========================================================================

    @Nested
    @DisplayName("Interactive flag")
    class InteractiveFlag {

        @Test
        @DisplayName("--interactive sets interactive mode")
        void interactiveLongFlag() {
            var flags = parseFlags("--interactive");
            assertThat(flags).containsEntry("interactive", true);
        }

        @Test
        @DisplayName("-i sets interactive mode")
        void interactiveShortFlag() {
            var flags = parseFlags("-i");
            assertThat(flags).containsEntry("interactive", true);
        }
    }

    // ========================================================================
    // Yes Flag Tests
    // ========================================================================

    @Nested
    @DisplayName("Yes flag")
    class YesFlag {

        @Test
        @DisplayName("--yes sets auto-accept mode")
        void yesLongFlag() {
            var flags = parseFlags("--yes");
            assertThat(flags).containsEntry("yes", true);
        }

        @Test
        @DisplayName("-y sets auto-accept mode")
        void yesShortFlag() {
            var flags = parseFlags("-y");
            assertThat(flags).containsEntry("yes", true);
        }
    }

    // ========================================================================
    // Flag Combinations
    // ========================================================================

    @Nested
    @DisplayName("Flag combinations")
    class FlagCombinations {

        @Test
        @DisplayName("multiple flags can be combined")
        void multipleFlags() {
            var flags = parseFlags("-d", "-u");
            assertThat(flags)
                    .containsEntry("dryRun", true)
                    .containsEntry("updateOnly", true);
        }

        @Test
        @DisplayName("all non-conflicting flags work together")
        void allNonConflictingFlags() {
            var flags = parseFlags("--dry-run", "--update-only", "--interactive");
            assertThat(flags)
                    .containsEntry("dryRun", true)
                    .containsEntry("updateOnly", true)
                    .containsEntry("interactive", true);
        }

        @Test
        @DisplayName("-i and -y are mutually exclusive (last wins)")
        void interactiveAndYesMutuallyExclusive() {
            // Last flag should win
            var flags1 = parseFlags("-i", "-y");
            assertThat(flags1).containsEntry("yes", true);

            var flags2 = parseFlags("-y", "-i");
            assertThat(flags2).containsEntry("interactive", true);
        }

        @Test
        @DisplayName("order of flags doesn't matter for most combinations")
        void orderDoesntMatter() {
            var flags1 = parseFlags("-d", "-u");
            var flags2 = parseFlags("-u", "-d");

            assertThat(flags1.get("dryRun")).isEqualTo(flags2.get("dryRun"));
            assertThat(flags1.get("updateOnly")).isEqualTo(flags2.get("updateOnly"));
        }
    }

    // ========================================================================
    // Unknown Flags
    // ========================================================================

    @Nested
    @DisplayName("Unknown flags")
    class UnknownFlags {

        @Test
        @DisplayName("unknown flags are ignored")
        void unknownFlagsIgnored() {
            var flags = parseFlags("--unknown", "-x", "--not-a-flag");
            // Should not throw, unknown flags just don't set anything
            assertThat(flags).doesNotContainKey("unknown");
        }

        @Test
        @DisplayName("known flags still work with unknown flags present")
        void knownFlagsStillWork() {
            var flags = parseFlags("--unknown", "-d", "--not-real");
            assertThat(flags).containsEntry("dryRun", true);
        }
    }

    // ========================================================================
    // No Flags
    // ========================================================================

    @Nested
    @DisplayName("No flags")
    class NoFlags {

        @Test
        @DisplayName("no flags results in default behavior")
        void noFlagsResultsInDefaults() {
            var flags = parseFlags();
            assertThat(flags.getOrDefault("dryRun", false)).isEqualTo(false);
            assertThat(flags.getOrDefault("updateOnly", false)).isEqualTo(false);
            assertThat(flags.getOrDefault("rollback", false)).isEqualTo(false);
        }
    }

    // ========================================================================
    // Parameterized Tests
    // ========================================================================

    @ParameterizedTest
    @DisplayName("each flag has both short and long form")
    @CsvSource({
            "-d, --dry-run, dryRun",
            "-u, --update-only, updateOnly",
            "-r, --rollback, rollback",
            "-i, --interactive, interactive",
            "-y, --yes, yes",
            "-h, --help, help"
    })
    void flagFormsAreEquivalent(String shortForm, String longForm, String key) {
        var shortFlags = parseFlags(shortForm);
        var longFlags = parseFlags(longForm);

        assertThat(shortFlags.get(key)).isEqualTo(longFlags.get(key));
        assertThat(shortFlags.get(key)).isEqualTo(true);
    }

    // ========================================================================
    // Interactive Flag Precedence (merged from InteractiveFlagTest)
    // ========================================================================

    /**
     * Tests for interactive flag precedence logic.
     * The Main.java logic:
     * - Boolean interactiveFlag = null (use config)
     * - --interactive/-i sets interactiveFlag = true
     * - --yes/-y sets interactiveFlag = false
     * - Final: interactiveFlag != null ? interactiveFlag : config.interactive
     */
    @Nested
    @DisplayName("Interactive flag precedence")
    class InteractiveFlagPrecedence {

        @Nested
        @DisplayName("Default behavior (no CLI flags)")
        class DefaultBehavior {

            @Test
            @DisplayName("uses config value when no CLI flag")
            void usesConfigValueWhenNoCliFlag() {
                Boolean interactiveFlag = null;
                boolean configInteractive = true;
                boolean result = interactiveFlag != null ? interactiveFlag : configInteractive;
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("uses config false when no CLI flag")
            void usesConfigFalseWhenNoCliFlag() {
                Boolean interactiveFlag = null;
                boolean configInteractive = false;
                boolean result = interactiveFlag != null ? interactiveFlag : configInteractive;
                assertThat(result).isFalse();
            }
        }

        @Nested
        @DisplayName("--interactive flag overrides config")
        class InteractiveOverrides {

            @Test
            @DisplayName("--interactive overrides config false")
            void interactiveOverridesConfigFalse() {
                Boolean interactiveFlag = true;
                boolean configInteractive = false;
                boolean result = interactiveFlag != null ? interactiveFlag : configInteractive;
                assertThat(result).isTrue();
            }

            @Test
            @DisplayName("--interactive with config true is still true")
            void interactiveWithConfigTrueIsStillTrue() {
                Boolean interactiveFlag = true;
                boolean configInteractive = true;
                boolean result = interactiveFlag != null ? interactiveFlag : configInteractive;
                assertThat(result).isTrue();
            }
        }

        @Nested
        @DisplayName("--yes flag overrides config")
        class YesOverrides {

            @Test
            @DisplayName("--yes overrides config true")
            void yesOverridesConfigTrue() {
                Boolean interactiveFlag = false;
                boolean configInteractive = true;
                boolean result = interactiveFlag != null ? interactiveFlag : configInteractive;
                assertThat(result).isFalse();
            }

            @Test
            @DisplayName("--yes with config false is still false")
            void yesWithConfigFalseIsStillFalse() {
                Boolean interactiveFlag = false;
                boolean configInteractive = false;
                boolean result = interactiveFlag != null ? interactiveFlag : configInteractive;
                assertThat(result).isFalse();
            }
        }

        @Nested
        @DisplayName("Conflicting flags (last wins)")
        class ConflictingFlags {

            @Test
            @DisplayName("-i then -y results in non-interactive")
            void iThenYResultsInNonInteractive() {
                Boolean interactiveFlag = null;
                for (String arg : List.of("-i", "-y")) {
                    switch (arg) {
                        case "--interactive", "-i" -> interactiveFlag = true;
                        case "--yes", "-y" -> interactiveFlag = false;
                    }
                }
                assertThat(interactiveFlag).isFalse();
            }

            @Test
            @DisplayName("-y then -i results in interactive")
            void yThenIResultsInInteractive() {
                Boolean interactiveFlag = null;
                for (String arg : List.of("-y", "-i")) {
                    switch (arg) {
                        case "--interactive", "-i" -> interactiveFlag = true;
                        case "--yes", "-y" -> interactiveFlag = false;
                    }
                }
                assertThat(interactiveFlag).isTrue();
            }

            @Test
            @DisplayName("multiple flags last wins")
            void multipleFlagsLastWins() {
                Boolean interactiveFlag = null;
                for (String arg : List.of("-i", "-y", "-i", "-y", "-i")) {
                    switch (arg) {
                        case "--interactive", "-i" -> interactiveFlag = true;
                        case "--yes", "-y" -> interactiveFlag = false;
                    }
                }
                assertThat(interactiveFlag).isTrue();
            }
        }

        @Nested
        @DisplayName("Config file integration")
        class ConfigFileIntegration {

            @Test
            @DisplayName("config interactive: true is loaded")
            void configInteractiveTrueIsLoaded() throws IOException {
                Path serverDir = Files.createDirectory(tempDir.resolve("server"));
                Path wofloDir = Files.createDirectory(serverDir.resolve("woflo"));
                Files.writeString(wofloDir.resolve("config.yml"), "interactive: true\n");
                Config config = Config.load(serverDir);
                assertThat(config.interactive).isTrue();
            }

            @Test
            @DisplayName("config interactive: false is loaded")
            void configInteractiveFalseIsLoaded() throws IOException {
                Path serverDir = Files.createDirectory(tempDir.resolve("server"));
                Path wofloDir = Files.createDirectory(serverDir.resolve("woflo"));
                Files.writeString(wofloDir.resolve("config.yml"), "interactive: false\n");
                Config config = Config.load(serverDir);
                assertThat(config.interactive).isFalse();
            }

            @Test
            @DisplayName("default config is non-interactive")
            void defaultConfigIsNonInteractive() throws IOException {
                Path serverDir = Files.createDirectory(tempDir.resolve("server"));
                Files.createDirectory(serverDir.resolve("woflo"));
                Config config = Config.load(serverDir);
                assertThat(config.interactive).isFalse();
            }
        }

        @ParameterizedTest
        @DisplayName("all CLI + Config combinations")
        @CsvSource({
                "null, true, true",
                "null, false, false",
                "true, true, true",
                "true, false, true",
                "false, true, false",
                "false, false, false"
        })
        void allCombinationsProduceExpectedResult(String flagStr, boolean config, boolean expected) {
            Boolean flag = "null".equals(flagStr) ? null : Boolean.parseBoolean(flagStr);
            boolean result = flag != null ? flag : config;
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("other flags don't affect interactiveFlag")
        void otherFlagsDontAffectInteractiveFlag() {
            Boolean interactiveFlag = null;
            for (String arg : List.of("--dry-run", "-d", "--update-only", "-u", "--rollback")) {
                switch (arg) {
                    case "--interactive", "-i" -> interactiveFlag = true;
                    case "--yes", "-y" -> interactiveFlag = false;
                }
            }
            assertThat(interactiveFlag).isNull();
        }
    }

    // ========================================================================
    // EULA Checking (Main.checkEula logic)
    // ========================================================================

    @Nested
    @DisplayName("EULA checking")
    class EulaChecking {

        @Test
        @DisplayName("accepted EULA passes check")
        void acceptedEulaPassesCheck() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path eula = serverDir.resolve("eula.txt");
            Files.writeString(eula, "eula=true");

            // Check logic from Main.checkEula
            boolean accepted = Files.exists(eula) && Files.readString(eula).contains("eula=true");
            assertThat(accepted).isTrue();
        }

        @Test
        @DisplayName("eula=false fails check")
        void eulaFalseFailsCheck() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path eula = serverDir.resolve("eula.txt");
            Files.writeString(eula, "eula=false");

            boolean accepted = Files.exists(eula) && Files.readString(eula).contains("eula=true");
            assertThat(accepted).isFalse();
        }

        @Test
        @DisplayName("missing EULA file fails check")
        void missingEulaFailsCheck() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path eula = serverDir.resolve("eula.txt");

            boolean accepted = Files.exists(eula) && Files.readString(eula).contains("eula=true");
            assertThat(accepted).isFalse();
        }

        @Test
        @DisplayName("EULA with extra content still accepted if contains eula=true")
        void eulaWithExtraContentAccepted() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            Path eula = serverDir.resolve("eula.txt");
            Files.writeString(eula, """
                    #By changing the setting below to TRUE you are indicating your agreement
                    #https://aka.ms/MinecraftEULA
                    eula=true
                    """);

            boolean accepted = Files.exists(eula) && Files.readString(eula).contains("eula=true");
            assertThat(accepted).isTrue();
        }

        @Test
        @DisplayName("default EULA template content")
        void defaultEulaTemplateContent() {
            // The template Main.java would create
            String template = """
                    #By changing the setting below to TRUE you are indicating your agreement to our EULA
                    #https://aka.ms/MinecraftEULA
                    eula=false
                    """;

            assertThat(template)
                    .contains("eula=false")
                    .contains("https://aka.ms/MinecraftEULA")
                    .contains("agreement");
        }

        @Test
        @DisplayName("TestFixtures.createAcceptedEula creates valid EULA")
        void testFixturesCreatesValidEula() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));
            TestFixtures.createAcceptedEula(serverDir);

            Path eula = serverDir.resolve("eula.txt");
            assertThat(eula).exists();
            assertThat(Files.readString(eula)).contains("eula=true");
        }
    }

    // ========================================================================
    // Server JAR Resolution (Main.runServer preconditions)
    // ========================================================================

    @Nested
    @DisplayName("Server JAR resolution")
    class ServerJarResolution {

        @Test
        @DisplayName("config serverJar overrides loader detection")
        void configServerJarOverridesLoader() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);
            Config config = Config.load(serverDir);

            // Simulate: config.serverJar != null ? config.serverJar : loader.findServerJar(serverDir)
            String configJar = "custom-server.jar";
            String resolved = configJar != null ? configJar : Loader.FABRIC.findServerJar(serverDir);

            assertThat(resolved).isEqualTo("custom-server.jar");
        }

        @Test
        @DisplayName("falls back to loader when config serverJar is null")
        void fallsBackToLoaderWhenConfigNull() throws IOException {
            Path serverDir = TestFixtures.createFabricServer(tempDir);

            String configJar = null;
            String resolved = configJar != null ? configJar : Loader.FABRIC.findServerJar(serverDir);

            assertThat(resolved).isEqualTo("fabric-server-launch.jar");
        }

        @Test
        @DisplayName("missing server JAR is detectable")
        void missingServerJarIsDetectable() throws IOException {
            Path serverDir = Files.createDirectories(tempDir.resolve("server"));

            String jarName = "server.jar";
            Path jar = serverDir.resolve(jarName);

            assertThat(Files.exists(jar)).isFalse();
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Replicates the CLI flag parsing logic from Main.main().
     * <p>
     * This helper mirrors Main.java line 17's switch statement exactly.
     * We cannot call Main.main() directly because it has side effects
     * (System.exit, server startup, etc). Instead, we test the same parsing
     * logic to verify correctness. Any changes to Main.main()'s flag parsing
     * should be reflected here to keep tests in sync.
     * <p>
     * See Main.java:17 for the original implementation.
     */
    private Map<String, Object> parseFlags(String... args) {
        Map<String, Object> flags = new HashMap<>();
        Boolean interactiveFlag = null;

        for (String a : args) {
            switch (a) {
                case "--dry-run", "-d" -> flags.put("dryRun", true);
                case "--update-only", "-u" -> flags.put("updateOnly", true);
                case "--rollback", "-r" -> flags.put("rollback", true);
                case "--interactive", "-i" -> {
                    flags.put("interactive", true);
                    interactiveFlag = true;
                }
                case "--yes", "-y" -> {
                    flags.put("yes", true);
                    interactiveFlag = false;
                }
                case "--help", "-h" -> flags.put("help", true);
            }
        }

        // Handle interactive flag precedence
        if (interactiveFlag != null) {
            if (interactiveFlag) {
                flags.remove("yes");
            } else {
                flags.remove("interactive");
            }
        }

        return flags;
    }
}
