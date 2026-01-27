package dev.woflo.fabric;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test category tags for organizing and filtering test execution.
 * <p>
 * Usage:
 * <pre>
 * {@code @UnitTest}
 * class MyTest { ... }
 * </pre>
 * <p>
 * Run specific categories:
 * <pre>
 * ./gradlew unitTest
 * ./gradlew integrationTest
 * ./gradlew loaderTest
 * ./gradlew apiTest
 * </pre>
 */
public final class TestTags {

    private TestTags() {
        // Utility class
    }

    /**
     * Unit tests - fast, isolated tests with no external dependencies.
     * These tests mock all external interactions.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("unit")
    public @interface UnitTest {
    }

    /**
     * Integration tests - tests that verify component interactions.
     * May use real file systems (via @TempDir) but no network.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("integration")
    public @interface IntegrationTest {
    }

    /**
     * Loader-specific tests - tests parameterized across all 8 loaders.
     * Verify loader detection, installation, and configuration.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("loader")
    public @interface LoaderTest {
    }

    /**
     * API tests - tests that verify external API integration using mocks.
     * Uses WireMock for HTTP stubbing.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("api")
    public @interface ApiTest {
    }

    /**
     * Slow tests - tests that take longer than 5 seconds.
     * Excluded from default test runs.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("slow")
    public @interface SlowTest {
    }

    /**
     * Network tests - tests that require real network access.
     * Excluded from default test runs, used for smoke testing.
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Tag("network")
    public @interface NetworkTest {
    }
}
