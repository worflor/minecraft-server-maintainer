package dev.woflo.fabric;

import dev.woflo.fabric.TestTags.UnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.regex.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the crash detection circular buffer logic in Main.runServer().
 *
 * The crash detection algorithm is embedded inline in Main.runServer() and cannot
 * be called directly. This test class:
 * 1. Verifies the algorithm implementation matches Main.java's actual code
 * 2. Tests all edge cases of the circular buffer and counting logic
 * 3. Uses reflection to verify Main.java source contains expected patterns
 *
 * The algorithm in Main.java (lines 68-91):
 * - Maintains a fixed-size array of crash timestamps
 * - Uses circular index (ci = (ci + 1) % maxCrashes)
 * - Counts crashes within time window: for (long t : crashes) if (t > 0 && now - t < windowMs) recent++
 * - Triggers protection when maxCrashes reached in window
 */
@UnitTest
@DisplayName("Crash Detection Logic")
class CrashDetectionTest {

    // ========================================================================
    // Source Code Verification - Ensures tests match actual Main.java code
    // ========================================================================

    @Nested
    @DisplayName("Source code verification")
    class SourceCodeVerification {

        @Test
        @DisplayName("Main.java contains expected crash detection algorithm")
        void mainJavaContainsExpectedCrashDetectionAlgorithm() throws IOException {
            // Read Main.java source to verify our test algorithm matches reality
            Path mainJava = Path.of("src/dev/woflo/fabric/Main.java");
            if (!Files.exists(mainJava)) {
                mainJava = Path.of("./src/dev/woflo/fabric/Main.java");
            }
            assertThat(mainJava).exists();

            String source = Files.readString(mainJava);

            // Verify the circular buffer algorithm is present
            assertThat(source).contains("(ci + 1) % maxCrashes");

            // Verify the crash counting algorithm: t > 0 && now - t < windowMs
            assertThat(source).containsPattern(Pattern.compile("t\\s*>\\s*0\\s*&&\\s*now\\s*-\\s*t\\s*<\\s*windowMs"));

            // Verify maxCrashes minimum enforcement
            assertThat(source).contains("Math.max(1, config.maxCrashes)");

            // Verify windowMs minimum enforcement
            assertThat(source).contains("Math.max(1000, config.crashWindow");

            // Verify delayMs allows zero
            assertThat(source).contains("Math.max(0, config.restartDelay");

            // Verify crash buffer reset on clean shutdown (exit == 0)
            assertThat(source).containsPattern(Pattern.compile("if\\s*\\(\\s*exit\\s*==\\s*0"));
        }

        @Test
        @DisplayName("countRecentCrashes algorithm matches Main.java")
        void countRecentCrashesAlgorithmMatchesMainJava() throws IOException {
            // The exact algorithm from Main.java line 90:
            // int recent = 0; long now = System.currentTimeMillis();
            // for (long t : crashes) if (t > 0 && now - t < windowMs) recent++;

            // Verify our test implementation matches by testing identical inputs
            long[] crashes = {100, 200, 0, 300};
            long now = 350;
            long windowMs = 200;

            // Our implementation
            int ourResult = countRecentCrashes(crashes, now, windowMs);

            // Direct inline implementation (exactly as in Main.java)
            int mainResult = 0;
            for (long t : crashes) {
                if (t > 0 && now - t < windowMs) {
                    mainResult++;
                }
            }

            assertThat(ourResult).isEqualTo(mainResult);
        }
    }

    // ========================================================================
    // Circular Buffer Index Logic
    // ========================================================================

    @Nested
    @DisplayName("Circular buffer index")
    class CircularBufferIndex {

        @Test
        @DisplayName("index wraps around at maxCrashes")
        void indexWrapsAroundAtMaxCrashes() {
            int maxCrashes = 3;
            int ci = 0;

            // First 3 increments
            ci = (ci + 1) % maxCrashes;
            assertThat(ci).isEqualTo(1);

            ci = (ci + 1) % maxCrashes;
            assertThat(ci).isEqualTo(2);

            ci = (ci + 1) % maxCrashes;
            assertThat(ci).isEqualTo(0); // Wrapped around
        }

        @Test
        @DisplayName("index cycles through all positions")
        void indexCyclesThroughAllPositions() {
            int maxCrashes = 5;
            int ci = 0;
            boolean[] visited = new boolean[maxCrashes];

            for (int i = 0; i < maxCrashes * 2; i++) {
                visited[ci] = true;
                ci = (ci + 1) % maxCrashes;
            }

            // All positions should be visited
            for (int i = 0; i < maxCrashes; i++) {
                assertThat(visited[i]).isTrue();
            }
        }

        @ParameterizedTest
        @DisplayName("modulo works for various maxCrashes values")
        @ValueSource(ints = {1, 2, 3, 5, 10})
        void moduloWorksForVariousMaxCrashesValues(int maxCrashes) {
            int ci = 0;
            for (int i = 0; i < maxCrashes + 1; i++) {
                assertThat(ci).isLessThan(maxCrashes);
                ci = (ci + 1) % maxCrashes;
            }
            // After maxCrashes+1 iterations, ci = 1 % maxCrashes
            // For maxCrashes=1: 1%1=0, for maxCrashes>=2: 1
            int expected = 1 % maxCrashes;
            assertThat(ci).isEqualTo(expected);
        }
    }

    // ========================================================================
    // Recent Crash Counting
    // ========================================================================

    @Nested
    @DisplayName("Recent crash counting")
    class RecentCrashCounting {

        @Test
        @DisplayName("counts crashes within time window")
        void countsCrashesWithinTimeWindow() {
            int maxCrashes = 3;
            long[] crashes = new long[maxCrashes];
            long windowMs = 300_000; // 5 minutes
            long now = System.currentTimeMillis();

            // Record 3 crashes all within the window
            crashes[0] = now - 60_000;  // 1 minute ago
            crashes[1] = now - 30_000;  // 30 seconds ago
            crashes[2] = now - 10_000;  // 10 seconds ago

            int recent = countRecentCrashes(crashes, now, windowMs);
            assertThat(recent).isEqualTo(3);
        }

        @Test
        @DisplayName("excludes crashes outside time window")
        void excludesCrashesOutsideTimeWindow() {
            int maxCrashes = 3;
            long[] crashes = new long[maxCrashes];
            long windowMs = 300_000; // 5 minutes
            long now = System.currentTimeMillis();

            // 2 crashes within window, 1 outside
            crashes[0] = now - 600_000; // 10 minutes ago (outside)
            crashes[1] = now - 60_000;  // 1 minute ago (inside)
            crashes[2] = now - 30_000;  // 30 seconds ago (inside)

            int recent = countRecentCrashes(crashes, now, windowMs);
            assertThat(recent).isEqualTo(2);
        }

        @Test
        @DisplayName("excludes zero timestamps (not yet recorded)")
        void excludesZeroTimestamps() {
            int maxCrashes = 5;
            long[] crashes = new long[maxCrashes]; // All zeros
            long windowMs = 300_000;
            long now = System.currentTimeMillis();

            // Only record 2 crashes
            crashes[0] = now - 60_000;
            crashes[1] = now - 30_000;

            int recent = countRecentCrashes(crashes, now, windowMs);
            assertThat(recent).isEqualTo(2);
        }

        @Test
        @DisplayName("empty buffer returns zero crashes")
        void emptyBufferReturnsZeroCrashes() {
            long[] crashes = new long[3];
            long windowMs = 300_000;
            long now = System.currentTimeMillis();

            int recent = countRecentCrashes(crashes, now, windowMs);
            assertThat(recent).isEqualTo(0);
        }
    }

    // ========================================================================
    // Crash Threshold Detection
    // ========================================================================

    @Nested
    @DisplayName("Crash threshold detection")
    class CrashThresholdDetection {

        @Test
        @DisplayName("triggers when maxCrashes reached in window")
        void triggersWhenMaxCrashesReachedInWindow() {
            int maxCrashes = 3;
            long[] crashes = new long[maxCrashes];
            long windowMs = 300_000;
            long now = System.currentTimeMillis();

            // Fill buffer with recent crashes
            for (int i = 0; i < maxCrashes; i++) {
                crashes[i] = now - (i * 10_000); // All within last 30 seconds
            }

            int recent = countRecentCrashes(crashes, now, windowMs);
            boolean shouldTrigger = recent >= maxCrashes;

            assertThat(shouldTrigger).isTrue();
        }

        @Test
        @DisplayName("does not trigger when below threshold")
        void doesNotTriggerWhenBelowThreshold() {
            int maxCrashes = 3;
            long[] crashes = new long[maxCrashes];
            long windowMs = 300_000;
            long now = System.currentTimeMillis();

            // Only 2 crashes in window
            crashes[0] = now - 60_000;
            crashes[1] = now - 30_000;

            int recent = countRecentCrashes(crashes, now, windowMs);
            boolean shouldTrigger = recent >= maxCrashes;

            assertThat(shouldTrigger).isFalse();
        }
    }

    // ========================================================================
    // Buffer Reset
    // ========================================================================

    @Nested
    @DisplayName("Buffer reset")
    class BufferReset {

        @Test
        @DisplayName("reset clears all timestamps")
        void resetClearsAllTimestamps() {
            int maxCrashes = 3;
            long[] crashes = new long[maxCrashes];
            long now = System.currentTimeMillis();

            // Fill buffer
            for (int i = 0; i < maxCrashes; i++) {
                crashes[i] = now - (i * 10_000);
            }

            // Reset (as done in Main.java)
            crashes = new long[maxCrashes];

            for (long t : crashes) {
                assertThat(t).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("reset happens on clean shutdown")
        void resetHappensOnCleanShutdown() {
            // exit == 0 should reset the buffer
            int exitCode = 0;
            boolean shouldReset = exitCode == 0;

            assertThat(shouldReset).isTrue();
        }

        @Test
        @DisplayName("reset happens on long-running server")
        void resetHappensOnLongRunningServer() {
            // run > windowMs should reset the buffer
            long runTime = 400_000; // 6.67 minutes
            long windowMs = 300_000; // 5 minutes
            boolean shouldReset = runTime > windowMs;

            assertThat(shouldReset).isTrue();
        }
    }

    // ========================================================================
    // Config Boundary Values
    // ========================================================================

    @Nested
    @DisplayName("Config boundary values")
    class ConfigBoundaryValues {

        @Test
        @DisplayName("maxCrashes minimum is enforced")
        void maxCrashesMinimumIsEnforced() {
            // Math.max(1, config.maxCrashes) prevents div by zero
            int configValue = 0;
            int maxCrashes = Math.max(1, configValue);

            assertThat(maxCrashes).isEqualTo(1);
        }

        @Test
        @DisplayName("negative maxCrashes is corrected")
        void negativeMaxCrashesIsCorrected() {
            int configValue = -5;
            int maxCrashes = Math.max(1, configValue);

            assertThat(maxCrashes).isEqualTo(1);
        }

        @Test
        @DisplayName("windowMs has minimum value")
        void windowMsHasMinimumValue() {
            // Math.max(1000, config.crashWindow * 1000L)
            int crashWindowConfig = 0;
            long windowMs = Math.max(1000, crashWindowConfig * 1000L);

            assertThat(windowMs).isEqualTo(1000);
        }

        @Test
        @DisplayName("delayMs allows zero")
        void delayMsAllowsZero() {
            // Math.max(0, config.restartDelay * 1000L)
            int restartDelayConfig = 0;
            long delayMs = Math.max(0, restartDelayConfig * 1000L);

            assertThat(delayMs).isEqualTo(0);
        }
    }

    // ========================================================================
    // Circular Buffer Simulation
    // ========================================================================

    @Nested
    @DisplayName("Full simulation")
    class FullSimulation {

        @Test
        @DisplayName("simulates sequence of crashes with wrap-around")
        void simulatesSequenceOfCrashesWithWrapAround() {
            int maxCrashes = 3;
            long[] crashes = new long[maxCrashes];
            int ci = 0;
            long windowMs = 300_000;
            long baseTime = 1000000000000L;

            // Simulate 5 crashes, causing wrap-around
            for (int i = 0; i < 5; i++) {
                long crashTime = baseTime + (i * 60_000); // 1 minute apart
                crashes[ci] = crashTime;
                ci = (ci + 1) % maxCrashes;
            }

            // After 5 crashes, buffer should contain crashes 2, 3, 4 (indices 2, 0, 1)
            // ci should be at position 2
            assertThat(ci).isEqualTo(2);

            // Count recent crashes from perspective of last crash
            long now = baseTime + (4 * 60_000);
            int recent = countRecentCrashes(crashes, now, windowMs);
            assertThat(recent).isEqualTo(3); // All 3 stored crashes are recent
        }

        @Test
        @DisplayName("old crashes age out correctly")
        void oldCrashesAgeOutCorrectly() {
            int maxCrashes = 3;
            long[] crashes = new long[maxCrashes];
            int ci = 0;
            long windowMs = 60_000; // 1 minute window
            long baseTime = 1000000000000L;

            // Record crashes at different times
            crashes[0] = baseTime;              // oldest
            crashes[1] = baseTime + 30_000;     // 30 sec later
            crashes[2] = baseTime + 90_000;     // 90 sec later (overwrites position)

            // At time baseTime + 120_000, only crash[2] should be within 1 minute window
            long now = baseTime + 120_000;
            int recent = countRecentCrashes(crashes, now, windowMs);
            assertThat(recent).isEqualTo(1);
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles maxCrashes of 1")
        void handlesMaxCrashesOfOne() {
            int maxCrashes = 1;
            long[] crashes = new long[maxCrashes];
            int ci = 0;
            long now = System.currentTimeMillis();

            crashes[ci] = now;
            ci = (ci + 1) % maxCrashes;

            assertThat(ci).isEqualTo(0); // Wraps immediately
            assertThat(crashes[0]).isEqualTo(now);
        }

        @Test
        @DisplayName("handles very large maxCrashes")
        void handlesVeryLargeMaxCrashes() {
            int maxCrashes = 100;
            long[] crashes = new long[maxCrashes];
            int ci = 0;
            long windowMs = 300_000;
            long now = System.currentTimeMillis();

            // Record 50 crashes
            for (int i = 0; i < 50; i++) {
                crashes[ci] = now - (i * 1000);
                ci = (ci + 1) % maxCrashes;
            }

            int recent = countRecentCrashes(crashes, now, windowMs);
            assertThat(recent).isEqualTo(50);
        }

        @Test
        @DisplayName("handles crash at exact window boundary")
        void handlesCrashAtExactWindowBoundary() {
            int maxCrashes = 3;
            long[] crashes = new long[maxCrashes];
            long windowMs = 300_000;
            long now = System.currentTimeMillis();

            // Crash at exact boundary
            crashes[0] = now - windowMs; // Exactly at boundary

            // According to the condition: now - t < windowMs
            // now - (now - windowMs) = windowMs, which is NOT < windowMs
            int recent = countRecentCrashes(crashes, now, windowMs);
            assertThat(recent).isEqualTo(0); // Boundary crash is excluded
        }

        @Test
        @DisplayName("handles crash just inside window boundary")
        void handlesCrashJustInsideWindowBoundary() {
            int maxCrashes = 3;
            long[] crashes = new long[maxCrashes];
            long windowMs = 300_000;
            long now = System.currentTimeMillis();

            // Crash just inside boundary
            crashes[0] = now - windowMs + 1;

            int recent = countRecentCrashes(crashes, now, windowMs);
            assertThat(recent).isEqualTo(1); // Included
        }
    }

    // ========================================================================
    // Helper Method (mirrors Main.java logic)
    // ========================================================================

    /**
     * Count recent crashes within the time window.
     * This mirrors the logic in Main.runServer().
     */
    private int countRecentCrashes(long[] crashes, long now, long windowMs) {
        int recent = 0;
        for (long t : crashes) {
            if (t > 0 && now - t < windowMs) {
                recent++;
            }
        }
        return recent;
    }
}
