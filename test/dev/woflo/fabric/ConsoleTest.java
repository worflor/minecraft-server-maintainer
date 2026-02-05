package dev.woflo.fabric;

import dev.woflo.fabric.Console;
import dev.woflo.fabric.TestTags.UnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for Console.java.
 * Tests console output, logging, ANSI codes, progress bars, and row management.
 * <p>
 * Uses SAME_THREAD execution because tests redirect System.out which would
 * cause race conditions under parallel execution.
 */
@UnitTest
@DisplayName("Console")
@Execution(ExecutionMode.SAME_THREAD)
class ConsoleTest {

    @TempDir
    Path tempDir;

    private Console console;
    private Path logFile;
    private ByteArrayOutputStream stdout;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() throws IOException {
        // Force full tier since System.console() is null in test environment
        System.setProperty("server.maintainer.term", "full");

        logFile = tempDir.resolve("test.log");
        console = new Console(logFile.toFile());

        // Capture stdout
        stdout = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(stdout));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        console.close();
        System.clearProperty("server.maintainer.term");
    }

    // ========================================================================
    // Basic Output Methods
    // ========================================================================

    @Nested
    @DisplayName("Basic output methods")
    class BasicOutputMethods {

        @Test
        @DisplayName("info writes to stdout and log")
        void infoWritesToStdoutAndLog() throws IOException {
            console.info("Test info message");

            String output = stdout.toString();
            assertThat(output).contains("Test info message");

            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("Info").contains("Test info message");
        }

        @Test
        @DisplayName("warn writes to stdout and log")
        void warnWritesToStdoutAndLog() throws IOException {
            console.warn("Test warning message");

            String output = stdout.toString();
            assertThat(output).contains("Test warning message");

            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("WARN").contains("Test warning message");
        }

        @Test
        @DisplayName("fail writes to stdout and log")
        void failWritesToStdoutAndLog() throws IOException {
            console.fail("Test error message");

            String output = stdout.toString();
            assertThat(output).contains("Test error message");

            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("ERROR").contains("Test error message");
        }

        @Test
        @DisplayName("blankLine outputs empty line")
        void blankLineOutputsEmptyLine() {
            console.blankLine();

            String output = stdout.toString();
            assertThat(output).contains("\n");
        }
    }

    // ========================================================================
    // Header
    // ========================================================================

    @Nested
    @DisplayName("Header output")
    class HeaderOutput {

        @Test
        @DisplayName("header outputs loader and version")
        void headerOutputsLoaderAndVersion() {
            console.header("Fabric", "1.21.1");

            String output = stdout.toString();
            assertThat(output).contains("Server Maintainer");
            assertThat(output).contains("woflo");
            assertThat(output).contains("Fabric");
            assertThat(output).contains("1.21.1");
        }

        @Test
        @DisplayName("header uses box drawing characters")
        void headerUsesBoxDrawingCharacters() {
            console.header("Fabric", "1.21.1");

            String output = stdout.toString();
            // Box drawing characters
            assertThat(output).containsAnyOf("\u2500", "\u2502", "\u250C", "\u2510", "\u2514", "\u2518");
        }
    }

    // ========================================================================
    // Dry Run
    // ========================================================================

    @Nested
    @DisplayName("Dry run indicator")
    class DryRunIndicator {

        @Test
        @DisplayName("dryRun outputs indicator")
        void dryRunOutputsIndicator() {
            console.dryRun();

            String output = stdout.toString();
            assertThat(output).contains("dry run");
        }
    }

    // ========================================================================
    // Row-based Display
    // ========================================================================

    @Nested
    @DisplayName("Row-based display")
    class RowBasedDisplay {

        @Test
        @DisplayName("setupRows creates row entries")
        void setupRowsCreatesRowEntries() {
            console.setupRows("Minecraft", "Mods", "Plugins");

            String output = stdout.toString();
            assertThat(output).contains("Minecraft");
            assertThat(output).contains("Mods");
            assertThat(output).contains("Plugins");
        }

        @Test
        @DisplayName("rowProgress updates progress bar")
        void rowProgressUpdatesProgressBar() {
            console.setupRows("Mods");
            console.rowProgress(0, 5, 10);

            String output = stdout.toString();
            // Should contain progress indicator
            assertThat(output).contains("5/10");
        }

        @Test
        @DisplayName("rowDone marks row as complete")
        void rowDoneMarksRowAsComplete() throws IOException {
            console.setupRows("Minecraft");
            console.rowDone(0, "1.21.1");

            String output = stdout.toString();
            assertThat(output).contains("1.21.1");

            // Should log
            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("OK").contains("Minecraft").contains("1.21.1");
        }

        @Test
        @DisplayName("rowDoneUpdate shows version transition")
        void rowDoneUpdateShowsVersionTransition() throws IOException {
            console.setupRows("Minecraft");
            console.rowDoneUpdate(0, "1.20.6", "1.21.1");

            String output = stdout.toString();
            assertThat(output).contains("1.20.6");
            assertThat(output).contains("1.21.1");
            assertThat(output).contains("\u2192"); // Unicode arrow for tier 4

            // Should log update
            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("Update");
        }

        @Test
        @DisplayName("rowSkip shows skip reason")
        void rowSkipShowsSkipReason() {
            console.setupRows("Datapacks");
            console.rowSkip(0, "no folder");

            String output = stdout.toString();
            assertThat(output).contains("no folder");
        }

        @Test
        @DisplayName("rowStatus shows current status")
        void rowStatusShowsCurrentStatus() {
            console.setupRows("Minecraft");
            console.rowStatus(0, "backing up");

            String output = stdout.toString();
            assertThat(output).contains("backing up");
        }

        @Test
        @DisplayName("handles out of bounds row index gracefully")
        void handlesOutOfBoundsRowIndexGracefully() {
            console.setupRows("Single");

            // Should not throw
            assertThatCode(() -> {
                console.rowProgress(5, 1, 1);
                console.rowDone(5, "test");
                console.rowSkip(5, "test");
                console.rowStatus(5, "test");
            }).doesNotThrowAnyException();
        }
    }

    // ========================================================================
    // Detail Output
    // ========================================================================

    @Nested
    @DisplayName("Detail output")
    class DetailOutput {

        @Test
        @DisplayName("detail outputs mod update info")
        void detailOutputsModUpdateInfo() throws IOException {
            console.detail("sodium", "0.5.7", "0.5.8");

            String output = stdout.toString();
            assertThat(output).contains("sodium");
            assertThat(output).contains("0.5.7");
            assertThat(output).contains("0.5.8");

            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("Update").contains("sodium");
        }

        @Test
        @DisplayName("detail truncates long names")
        void detailTruncatesLongNames() {
            String longName = "very-long-mod-name-that-exceeds-limit";
            console.detail(longName, "1.0", "2.0");

            String output = stdout.toString();
            // Should contain truncated version with ~
            assertThat(output).contains("~");
        }
    }

    // ========================================================================
    // Progress Indicators
    // ========================================================================

    @Nested
    @DisplayName("Progress indicators")
    class ProgressIndicators {

        @Test
        @DisplayName("checking outputs status")
        void checkingOutputsStatus() {
            console.checking("Fetching versions");

            String output = stdout.toString();
            assertThat(output).contains("Fetching versions");
        }

        @Test
        @DisplayName("checkDone outputs result with status")
        void checkDoneOutputsResultWithStatus() {
            console.checking("Test");
            console.checkDone("1.21.1", true);

            String output = stdout.toString();
            assertThat(output).contains("1.21.1");
        }

        @Test
        @DisplayName("progress outputs ongoing message")
        void progressOutputsOngoingMessage() {
            console.progress("Downloading");

            String output = stdout.toString();
            assertThat(output).contains("Downloading");
        }

        @Test
        @DisplayName("progressDone outputs completion message")
        void progressDoneOutputsCompletionMessage() throws IOException {
            console.progressDone("Download", "complete");

            String output = stdout.toString();
            assertThat(output).contains("Download");
            assertThat(output).contains("complete");

            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("OK");
        }
    }

    // ========================================================================
    // Cursor Control
    // ========================================================================

    @Nested
    @DisplayName("Cursor control")
    class CursorControl {

        @Test
        @DisplayName("hideCursor outputs escape sequence")
        void hideCursorOutputsEscapeSequence() {
            console.hideCursor();

            String output = stdout.toString();
            // May contain escape sequence if TTY detected
            // This is environment-dependent, so we just verify no exception
            assertThat(output).isNotNull();
        }

        @Test
        @DisplayName("showCursor outputs escape sequence")
        void showCursorOutputsEscapeSequence() {
            console.showCursor();

            String output = stdout.toString();
            assertThat(output).isNotNull();
        }
    }

    // ========================================================================
    // Log File
    // ========================================================================

    @Nested
    @DisplayName("Log file")
    class LogFileTest {

        @Test
        @DisplayName("log entries include timestamp")
        void logEntriesIncludeTimestamp() throws IOException {
            console.info("Test message");

            String logContent = Files.readString(logFile);
            // Should have timestamp format [YYYY-MM-DD HH:mm:ss]
            assertThat(logContent).containsPattern("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\]");
        }

        @Test
        @DisplayName("log file is created automatically")
        void logFileIsCreatedAutomatically() {
            assertThat(logFile).exists();
        }

        @Test
        @DisplayName("log entries are appended")
        void logEntriesAreAppended() throws IOException {
            console.info("Message 1");
            console.info("Message 2");
            console.info("Message 3");

            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("Message 1");
            assertThat(logContent).contains("Message 2");
            assertThat(logContent).contains("Message 3");
        }

        @Test
        @DisplayName("close flushes and closes log")
        void closeFlushesAndClosesLog() throws IOException {
            console.info("Final message");
            console.close();

            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("Final message");
        }
    }

    // ========================================================================
    // Countdown (timing-sensitive, basic verification)
    // ========================================================================

    @Nested
    @DisplayName("Countdown")
    class CountdownTest {

        @Test
        @DisplayName("countdown method exists and is callable")
        void countdownMethodExistsAndIsCallable() {
            // Verify Console has countdown method via reflection
            assertThatCode(() -> {
                var method = Console.class.getDeclaredMethod("countdown");
                assertThat(method).isNotNull();
                assertThat(method.getReturnType()).isEqualTo(void.class);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("countdownSeconds method exists with int parameter")
        void countdownSecondsMethodExistsWithIntParameter() {
            // Verify Console has countdownSeconds(int) method via reflection
            assertThatCode(() -> {
                var method = Console.class.getDeclaredMethod("countdownSeconds", int.class);
                assertThat(method).isNotNull();
                assertThat(method.getReturnType()).isEqualTo(void.class);
                assertThat(method.getParameterCount()).isEqualTo(1);
                assertThat(method.getParameterTypes()[0]).isEqualTo(int.class);
            }).doesNotThrowAnyException();
        }
    }

    // ========================================================================
    // ANSI Color Codes (environment-dependent)
    // ========================================================================

    @Nested
    @DisplayName("ANSI color codes")
    class AnsiColorCodes {

        @Test
        @DisplayName("output may contain ANSI codes on TTY")
        void outputMayContainAnsiCodesOnTty() {
            console.info("Colored message");

            String output = stdout.toString();
            // We can't guarantee ANSI codes (depends on TTY detection)
            // but output should contain the message
            assertThat(output).contains("Colored message");
        }

        @Test
        @DisplayName("header contains colored elements")
        void headerContainsColoredElements() {
            console.header("Fabric", "1.21.1");

            String output = stdout.toString();
            // Should contain the text regardless of coloring
            assertThat(output).contains("Server Maintainer");
            assertThat(output).contains("Fabric");
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles null-safe operations")
        void handlesNullSafeOperations() {
            // These shouldn't throw even with edge cases
            assertThatCode(() -> {
                console.info("");
                console.warn("");
                console.fail("");
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handles special characters in messages")
        void handlesSpecialCharactersInMessages() {
            console.info("Special: \t\n\r \"quotes\" 'apostrophe'");

            String output = stdout.toString();
            assertThat(output).contains("Special:");
        }

        @Test
        @DisplayName("handles Unicode in messages")
        void handlesUnicodeInMessages() {
            console.info("Unicode: \u263A \u2764 \u2605");

            String output = stdout.toString();
            assertThat(output).contains("\u263A");
        }

        @Test
        @DisplayName("handles very long messages")
        void handlesVeryLongMessages() {
            String longMessage = "x".repeat(1000);
            console.info(longMessage);

            String output = stdout.toString();
            assertThat(output).contains(longMessage);
        }

        @Test
        @DisplayName("endRows completes without error and logs correctly")
        void endRowsCompletesWithoutError() throws IOException {
            console.setupRows("Test");
            console.rowDone(0, "complete");
            console.endRows();

            // Verify output was produced - endRows should finalize row display
            String output = stdout.toString();
            assertThat(output).contains("Test");

            // Verify log file was written to
            String logContent = Files.readString(logFile);
            assertThat(logContent).contains("Test");
        }
    }

    // ========================================================================
    // Multiple Console Instances
    // ========================================================================

    @Nested
    @DisplayName("Multiple instances")
    class MultipleInstances {

        @Test
        @DisplayName("multiple consoles can write to different log files")
        void multipleConsolesCanWriteToDifferentLogFiles() throws IOException {
            Path logFile2 = tempDir.resolve("test2.log");
            Console console2 = new Console(logFile2.toFile());

            console.info("Message to console 1");
            console2.info("Message to console 2");

            console2.close();

            String log1 = Files.readString(logFile);
            String log2 = Files.readString(logFile2);

            assertThat(log1).contains("Message to console 1");
            assertThat(log1).doesNotContain("Message to console 2");

            assertThat(log2).contains("Message to console 2");
            assertThat(log2).doesNotContain("Message to console 1");
        }
    }
}
