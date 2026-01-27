package dev.woflo.fabric;

import dev.woflo.fabric.TestTags.UnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for version comparison logic.
 * Tests the real Updater.compareVersions() method via reflection.
 */
@UnitTest
@DisplayName("Version Comparison")
class VersionComparisonTest {

    private static Method compareVersionsMethod;

    @BeforeAll
    static void setupReflection() throws Exception {
        // Get the real Updater.compareVersions method via reflection
        compareVersionsMethod = Updater.class.getDeclaredMethod("compareVersions", String.class, String.class);
        compareVersionsMethod.setAccessible(true);
    }

    /**
     * Calls the real Updater.compareVersions() method via reflection.
     * This ensures tests verify actual production code behavior.
     */
    private static int compareVersions(String a, String b) {
        try {
            return (int) compareVersionsMethod.invoke(null, a, b);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke Updater.compareVersions", e);
        }
    }

    // ========================================================================
    // Basic Comparisons
    // ========================================================================

    @Nested
    @DisplayName("Basic comparisons")
    class BasicComparisons {

        @Test
        @DisplayName("equal versions return 0")
        void equalVersionsReturnZero() {
            assertThat(compareVersions("1.21.1", "1.21.1")).isEqualTo(0);
            assertThat(compareVersions("1.20", "1.20")).isEqualTo(0);
            assertThat(compareVersions("1.0.0", "1.0.0")).isEqualTo(0);
        }

        @Test
        @DisplayName("greater major version returns positive")
        void greaterMajorVersionReturnsPositive() {
            assertThat(compareVersions("2.0.0", "1.0.0")).isGreaterThan(0);
            assertThat(compareVersions("2.21.1", "1.21.1")).isGreaterThan(0);
        }

        @Test
        @DisplayName("lesser major version returns negative")
        void lesserMajorVersionReturnsNegative() {
            assertThat(compareVersions("1.0.0", "2.0.0")).isLessThan(0);
            assertThat(compareVersions("1.21.1", "2.21.1")).isLessThan(0);
        }

        @Test
        @DisplayName("greater minor version returns positive")
        void greaterMinorVersionReturnsPositive() {
            assertThat(compareVersions("1.21.0", "1.20.0")).isGreaterThan(0);
            assertThat(compareVersions("1.21.1", "1.20.6")).isGreaterThan(0);
        }

        @Test
        @DisplayName("lesser minor version returns negative")
        void lesserMinorVersionReturnsNegative() {
            assertThat(compareVersions("1.20.0", "1.21.0")).isLessThan(0);
            assertThat(compareVersions("1.20.6", "1.21.1")).isLessThan(0);
        }

        @Test
        @DisplayName("greater patch version returns positive")
        void greaterPatchVersionReturnsPositive() {
            assertThat(compareVersions("1.21.2", "1.21.1")).isGreaterThan(0);
            assertThat(compareVersions("1.20.10", "1.20.9")).isGreaterThan(0);
        }

        @Test
        @DisplayName("lesser patch version returns negative")
        void lesserPatchVersionReturnsNegative() {
            assertThat(compareVersions("1.21.1", "1.21.2")).isLessThan(0);
            assertThat(compareVersions("1.20.9", "1.20.10")).isLessThan(0);
        }
    }

    // ========================================================================
    // Numeric Comparison (Not Lexicographic)
    // ========================================================================

    @Nested
    @DisplayName("Numeric comparison")
    class NumericComparison {

        @Test
        @DisplayName("1.20.10 > 1.20.2 (numeric, not lexicographic)")
        void numericNotLexicographic() {
            // This is the key test - string comparison would say 1.20.10 < 1.20.2
            assertThat(compareVersions("1.20.10", "1.20.2")).isGreaterThan(0);
        }

        @Test
        @DisplayName("1.20.9 < 1.20.10 (numeric, not lexicographic)")
        void nineIsLessThanTen() {
            assertThat(compareVersions("1.20.9", "1.20.10")).isLessThan(0);
        }

        @Test
        @DisplayName("1.20.100 > 1.20.99")
        void largeNumbersWork() {
            assertThat(compareVersions("1.20.100", "1.20.99")).isGreaterThan(0);
        }

        @Test
        @DisplayName("2.0.0 > 1.99.99")
        void majorTrumpsMinor() {
            assertThat(compareVersions("2.0.0", "1.99.99")).isGreaterThan(0);
        }
    }

    // ========================================================================
    // Different Length Versions
    // ========================================================================

    @Nested
    @DisplayName("Different length versions")
    class DifferentLengthVersions {

        @Test
        @DisplayName("1.20 equals 1.20.0")
        void missingPatchTreatedAsZero() {
            assertThat(compareVersions("1.20", "1.20.0")).isEqualTo(0);
        }

        @Test
        @DisplayName("1.20.1 > 1.20")
        void versionWithPatchGreater() {
            assertThat(compareVersions("1.20.1", "1.20")).isGreaterThan(0);
        }

        @Test
        @DisplayName("1.20 < 1.20.1")
        void versionWithoutPatchLesser() {
            assertThat(compareVersions("1.20", "1.20.1")).isLessThan(0);
        }

        @Test
        @DisplayName("1 equals 1.0 equals 1.0.0")
        void singleDigitVersions() {
            assertThat(compareVersions("1", "1.0")).isEqualTo(0);
            assertThat(compareVersions("1.0", "1.0.0")).isEqualTo(0);
            assertThat(compareVersions("1", "1.0.0")).isEqualTo(0);
        }
    }

    // ========================================================================
    // Sorting
    // ========================================================================

    @Nested
    @DisplayName("Version sorting")
    class VersionSorting {

        @Test
        @DisplayName("sorts versions in ascending order")
        void sortsVersionsAscending() {
            List<String> versions = new ArrayList<>(List.of(
                    "1.21.1", "1.20.6", "1.20.4", "1.21", "1.20.10", "1.20.2"
            ));

            versions.sort(VersionComparisonTest::compareVersions);

            assertThat(versions).containsExactly(
                    "1.20.2", "1.20.4", "1.20.6", "1.20.10", "1.21", "1.21.1"
            );
        }

        @Test
        @DisplayName("finds max version correctly")
        void findsMaxVersionCorrectly() {
            List<String> versions = List.of(
                    "1.20.6", "1.21.1", "1.20.4", "1.21", "1.20.10"
            );

            String max = versions.stream()
                    .max(VersionComparisonTest::compareVersions)
                    .orElse(null);

            assertThat(max).isEqualTo("1.21.1");
        }

        @Test
        @DisplayName("finds min version correctly")
        void findsMinVersionCorrectly() {
            List<String> versions = List.of(
                    "1.20.6", "1.21.1", "1.20.4", "1.21", "1.20.10"
            );

            String min = versions.stream()
                    .min(VersionComparisonTest::compareVersions)
                    .orElse(null);

            assertThat(min).isEqualTo("1.20.4");
        }
    }

    // ========================================================================
    // Parameterized Tests
    // ========================================================================

    @ParameterizedTest
    @DisplayName("comprehensive version comparisons")
    @CsvSource({
            "1.20.6, 1.21.1, -1",
            "1.21.1, 1.20.6, 1",
            "1.21.1, 1.21.1, 0",
            "1.20.10, 1.20.2, 1",
            "1.20.2, 1.20.10, -1",
            "1.20, 1.20.1, -1",
            "1.20.1, 1.20, 1",
            "1.21, 1.20.99, 1",
            "2.0.0, 1.99.99, 1",
            "1.0, 1.0.0, 0",
            "1.20.0, 1.20, 0"
    })
    void comprehensiveVersionComparisons(String v1, String v2, int expectedSign) {
        int result = compareVersions(v1, v2);

        if (expectedSign < 0) {
            assertThat(result).isLessThan(0);
        } else if (expectedSign > 0) {
            assertThat(result).isGreaterThan(0);
        } else {
            assertThat(result).isEqualTo(0);
        }
    }

    // ========================================================================
    // Version Pattern Matching
    // ========================================================================

    @Nested
    @DisplayName("Version pattern matching")
    class VersionPatternMatching {

        private static final String VERSION_PATTERN = "\\d+\\.\\d+(\\.\\d+)?";

        @ParameterizedTest
        @DisplayName("valid version strings match pattern")
        @ValueSource(strings = {"1.20", "1.20.6", "1.21.1", "2.0.0", "1.0", "10.20.30"})
        void validVersionsMatchPattern(String version) {
            assertThat(version).matches(VERSION_PATTERN);
        }

        @ParameterizedTest
        @DisplayName("invalid version strings don't match pattern")
        @ValueSource(strings = {"1.20.6.1", "1.20-pre1", "24w38a", "1.20.6-rc1", "a.b.c"})
        void invalidVersionsDontMatchPattern(String version) {
            assertThat(version).doesNotMatch(VERSION_PATTERN);
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles large version numbers")
        void handlesLargeVersionNumbers() {
            assertThat(compareVersions("100.200.300", "100.200.299")).isGreaterThan(0);
            assertThat(compareVersions("999.999.999", "999.999.998")).isGreaterThan(0);
        }

        @Test
        @DisplayName("handles zero versions")
        void handlesZeroVersions() {
            assertThat(compareVersions("0.0.0", "0.0.0")).isEqualTo(0);
            assertThat(compareVersions("0.0.1", "0.0.0")).isGreaterThan(0);
            assertThat(compareVersions("1.0.0", "0.0.0")).isGreaterThan(0);
        }

        @Test
        @DisplayName("handles single digit versions")
        void handlesSingleDigitVersions() {
            assertThat(compareVersions("2", "1")).isGreaterThan(0);
            assertThat(compareVersions("1", "2")).isLessThan(0);
            assertThat(compareVersions("1", "1")).isEqualTo(0);
        }
    }

}
