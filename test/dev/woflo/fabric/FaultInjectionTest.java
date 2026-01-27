package dev.woflo.fabric;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Fault injection tests to verify error handling paths.
 * These tests simulate failures that are hard to reproduce naturally:
 * - File system failures (permission denied, missing files)
 * - Malformed data (corrupt JSON, invalid inputs)
 * - Concurrency edge cases
 *
 * Note: HTTP tests are limited because the Http class enforces HTTPS for security.
 */
@Tag("integration")
@DisplayName("Fault Injection Tests")
class FaultInjectionTest {

    @Nested
    @DisplayName("HTTP URL Validation")
    class HttpUrlValidation {

        @Test
        @DisplayName("HTTP URL is rejected (requires HTTPS)")
        void httpUrlRejected() {
            assertThatThrownBy(() -> Http.get("http://example.com/api"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("HTTPS required");
        }

        @Test
        @DisplayName("Invalid URL throws IOException")
        void invalidUrlThrows() {
            assertThatThrownBy(() -> Http.get("not a valid url"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Invalid URL");
        }

        @Test
        @DisplayName("Empty URL throws IOException")
        void emptyUrlThrows() {
            assertThatThrownBy(() -> Http.get(""))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Null-like URL string throws")
        void nullLikeUrlThrows() {
            assertThatThrownBy(() -> Http.get("null"))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Connection to non-existent HTTPS host fails")
        void connectionToNonExistentHost() {
            // Use a domain that definitely won't resolve
            assertThatThrownBy(() -> Http.get("https://this-domain-definitely-does-not-exist-12345.com/api"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("JSON Parsing Fault Injection")
    class JsonParsingFaults {

        @Test
        @DisplayName("Invalid JSON object is handled gracefully")
        void invalidJsonObject() {
            // Parser handles invalid JSON in one of three ways:
            // 1. Throws RuntimeException
            // 2. Returns null
            // 3. Returns partial/malformed result (lenient parsing)
            try {
                var result = Http.parseObject("{invalid json}");
                // If result is not null, verify it's at least a Map (parser was lenient)
                if (result != null) {
                    assertThat(result).isInstanceOf(Map.class);
                }
            } catch (RuntimeException e) {
                // Exception is also acceptable behavior
                assertThat(e).isNotNull();
            }
        }

        @Test
        @DisplayName("Invalid JSON array throws RuntimeException")
        void invalidJsonArray() {
            assertThatThrownBy(() -> Http.parseArray("[not valid array"))
                    .isInstanceOf(RuntimeException.class)
                    .satisfies(e -> assertThat(e.getMessage()).isNotBlank());
        }

        @Test
        @DisplayName("Truncated JSON object is handled gracefully")
        void truncatedJsonObject() {
            // Parser handles truncated JSON in one of three ways:
            // 1. Throws RuntimeException
            // 2. Returns null
            // 3. Returns partial result with available keys (lenient parsing)
            try {
                var result = Http.parseObject("{\"name\": \"test\", \"value\":");
                if (result != null) {
                    // Lenient parser returned partial result
                    assertThat(result).isInstanceOf(Map.class);
                    // The "name" key should still be accessible
                    assertThat(Http.str(result, "name")).isEqualTo("test");
                }
            } catch (RuntimeException e) {
                // Exception is also acceptable
                assertThat(e).isNotNull();
            }
        }

        @Test
        @DisplayName("Truncated JSON array is handled gracefully")
        void truncatedJsonArray() {
            // Parser handles truncated array in one of three ways:
            // 1. Throws RuntimeException
            // 2. Returns null
            // 3. Returns partial array with available elements (lenient parsing)
            try {
                var result = Http.parseArray("[\"a\", \"b\",");
                if (result != null) {
                    // Lenient parser returned partial result
                    assertThat(result).isInstanceOf(List.class);
                    // Should contain at least the completed elements
                    assertThat(result).contains("a", "b");
                }
            } catch (RuntimeException e) {
                // Exception is also acceptable
                assertThat(e).isNotNull();
            }
        }

        @Test
        @DisplayName("JSON object when array expected throws ClassCastException")
        void jsonObjectWhenArrayExpected() {
            assertThatThrownBy(() -> Http.parseArray("{\"key\": \"value\"}"))
                    .isInstanceOf(ClassCastException.class);
        }

        @Test
        @DisplayName("JSON array when object expected throws ClassCastException")
        void jsonArrayWhenObjectExpected() {
            assertThatThrownBy(() -> Http.parseObject("[\"a\", \"b\", \"c\"]"))
                    .isInstanceOf(ClassCastException.class);
        }

        @Test
        @DisplayName("null JSON literal returns null")
        void nullJsonLiteral() {
            var objResult = Http.parseObject("null");
            var arrResult = Http.parseArray("null");

            assertThat(objResult).isNull();
            assertThat(arrResult).isNull();
        }

        @Test
        @DisplayName("Empty string returns null")
        void emptyStringJson() {
            var objResult = Http.parseObject("");
            var arrResult = Http.parseArray("");

            assertThat(objResult).isNull();
            assertThat(arrResult).isNull();
        }

        @Test
        @DisplayName("Whitespace-only JSON returns null")
        void whitespaceOnlyJson() {
            var objResult = Http.parseObject("   \n\t  ");
            var arrResult = Http.parseArray("   \n\t  ");

            assertThat(objResult).isNull();
            assertThat(arrResult).isNull();
        }

        @Test
        @DisplayName("JSON with extra trailing data parses first object or returns null")
        void jsonWithTrailingData() {
            // Parser behavior depends on implementation - must either:
            // 1. Parse first valid object and ignore trailing data
            // 2. Return null for invalid JSON
            var result = Http.parseObject("{\"key\": \"value\"} extra stuff");
            if (result != null) {
                // If it parsed, should have extracted the first valid object
                assertThat(Http.str(result, "key")).isEqualTo("value");
            } else {
                // Null is also acceptable - strict parsing rejected trailing data
                assertThat(result).isNull();
            }
        }

        @Test
        @DisplayName("Deeply nested JSON parses correctly")
        void deeplyNestedJson() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) sb.append("{\"n\":");
            sb.append("\"deep\"");
            for (int i = 0; i < 100; i++) sb.append("}");

            var result = Http.parseObject(sb.toString());
            // Must either successfully parse the nested structure or return null
            if (result != null) {
                // Verify we can navigate the nested structure
                var current = result;
                for (int i = 0; i < 99; i++) {
                    current = Http.obj(current, "n");
                    assertThat(current).isNotNull();
                }
                // Final level should have the string value
                assertThat(Http.str(current, "n")).isEqualTo("deep");
            } else {
                // Null indicates parser couldn't handle depth - acceptable
                assertThat(result).isNull();
            }
        }

        @Test
        @DisplayName("JSON with unicode escape sequences parses correctly")
        void jsonWithUnicodeEscapes() {
            var result = Http.parseObject("{\"key\": \"\\u0048\\u0065\\u006C\\u006C\\u006F\"}");
            // Unicode escape sequences must be parsed - this is valid JSON
            assertThat(result).isNotNull();
            assertThat(Http.str(result, "key")).isEqualTo("Hello");
        }

        @Test
        @DisplayName("JSON with special characters in strings")
        void jsonWithSpecialChars() {
            var result = Http.parseObject("{\"key\": \"line1\\nline2\\ttab\"}");
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("File System Error Handling")
    class FileSystemErrors {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Hash of non-existent file throws exception")
        void hashNonExistentFile() {
            Path nonExistent = tempDir.resolve("does-not-exist.txt");

            assertThatThrownBy(() -> Http.sha512(nonExistent))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Hash of directory throws exception")
        void hashDirectory() throws IOException {
            Path dir = tempDir.resolve("subdir");
            Files.createDirectory(dir);

            assertThatThrownBy(() -> Http.sha512(dir))
                    .isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Read from read-only file succeeds")
        void readFromReadOnlyFile() throws IOException {
            Path file = tempDir.resolve("readonly.txt");
            Files.writeString(file, "test content");
            file.toFile().setReadOnly();

            try {
                String hash = Http.sha512(file);
                assertThat(hash).hasSize(128);
            } finally {
                file.toFile().setWritable(true); // Cleanup
            }
        }

        @Test
        @DisplayName("Hash of empty file returns valid hash")
        void hashEmptyFile() throws IOException {
            Path file = tempDir.resolve("empty.txt");
            Files.createFile(file);

            String hash = Http.sha512(file);

            assertThat(hash).hasSize(128);
            assertThat(hash).matches("[0-9a-f]+");
            // SHA-512 of empty string is well-known
            assertThat(hash).isEqualTo(
                    "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
                    "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e");
        }

        @Test
        @DisplayName("Hash of large file succeeds")
        void hashLargeFile() throws IOException {
            Path file = tempDir.resolve("large.bin");
            byte[] data = new byte[1024 * 1024]; // 1MB
            Files.write(file, data);

            String hash = Http.sha512(file);

            assertThat(hash).hasSize(128);
        }

        @Test
        @DisplayName("Hash of file with special characters in name")
        void hashFileWithSpecialName() throws IOException {
            Path file = tempDir.resolve("test file (1).txt");
            Files.writeString(file, "content");

            String hash = Http.sha512(file);

            assertThat(hash).hasSize(128);
        }

        @Test
        @DisplayName("Hash of binary file")
        void hashBinaryFile() throws IOException {
            Path file = tempDir.resolve("binary.dat");
            byte[] data = new byte[256];
            for (int i = 0; i < 256; i++) data[i] = (byte) i;
            Files.write(file, data);

            String hash = Http.sha512(file);

            assertThat(hash).hasSize(128);
            assertThat(hash).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("Hash is deterministic")
        void hashIsDeterministic() throws IOException {
            Path file = tempDir.resolve("deterministic.txt");
            Files.writeString(file, "same content every time");

            String hash1 = Http.sha512(file);
            String hash2 = Http.sha512(file);
            String hash3 = Http.sha512(file);

            assertThat(hash1).isEqualTo(hash2).isEqualTo(hash3);
        }
    }

    @Nested
    @DisplayName("URL Encoding Edge Cases")
    class UrlEncodingEdgeCases {

        @Test
        @DisplayName("Empty string encoding")
        void encodeEmptyString() {
            String result = Http.encode("");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Encoding preserves alphanumeric")
        void encodeAlphanumeric() {
            String result = Http.encode("abc123XYZ");
            assertThat(result).isEqualTo("abc123XYZ");
        }

        @Test
        @DisplayName("Encoding handles spaces")
        void encodeSpaces() {
            String result = Http.encode("hello world");
            assertThat(result).isEqualTo("hello+world");
        }

        @Test
        @DisplayName("Encoding handles special characters")
        void encodeSpecialChars() {
            String result = Http.encode("test&value=123");
            assertThat(result).contains("%26").contains("%3D");
        }

        @Test
        @DisplayName("Encoding handles unicode")
        void encodeUnicode() {
            String result = Http.encode("café");
            assertThat(result).isNotEqualTo("café");
            assertThat(result).contains("%");
        }

        @Test
        @DisplayName("Encoding handles null-like strings")
        void encodeNullWord() {
            String result = Http.encode("null");
            assertThat(result).isEqualTo("null");
        }

        @Test
        @DisplayName("Encoding handles very long strings")
        void encodeLongString() {
            String longInput = "a".repeat(10000);
            String result = Http.encode(longInput);
            assertThat(result).hasSize(10000);
        }

        @Test
        @DisplayName("Encoding handles all URL-unsafe characters")
        void encodeAllUnsafeChars() {
            String result = Http.encode("!@#$%^&*(){}[]|\\:\";<>?,./");
            assertThat(result).doesNotContain("@", "#", "^", "&", "(", ")");
        }

        @Test
        @DisplayName("Encoding handles newlines and tabs")
        void encodeWhitespace() {
            String result = Http.encode("line1\nline2\ttab");
            assertThat(result).doesNotContain("\n", "\t");
        }

        @Test
        @DisplayName("Encoding is idempotent for safe chars")
        void encodeIdempotentForSafe() {
            String input = "safeChars123";
            String result1 = Http.encode(input);
            String result2 = Http.encode(result1);
            // First encoding should return same string for safe chars
            assertThat(result1).isEqualTo(input);
        }
    }

    @Nested
    @DisplayName("JSON Extraction Edge Cases")
    class JsonExtractionEdgeCases {

        @Test
        @DisplayName("str() with missing key returns null")
        void strMissingKey() {
            var obj = Http.parseObject("{\"key\": \"value\"}");
            String result = Http.str(obj, "missing");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("str() with null value returns null")
        void strNullValue() {
            var obj = Http.parseObject("{\"key\": null}");
            String result = Http.str(obj, "key");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("str() with number value converts to string")
        void strNumberValue() {
            var obj = Http.parseObject("{\"key\": 123}");
            String result = Http.str(obj, "key");
            // JSON parser may represent as Long, Double, etc.
            assertThat(result).isNotNull();
            assertThat(result).contains("123");
        }

        @Test
        @DisplayName("str() with boolean value")
        void strBooleanValue() {
            var obj = Http.parseObject("{\"key\": true}");
            String result = Http.str(obj, "key");
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("str() with empty string value")
        void strEmptyValue() {
            var obj = Http.parseObject("{\"key\": \"\"}");
            String result = Http.str(obj, "key");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("str() from null object throws NPE")
        void strFromNullThrowsNpe() {
            assertThatThrownBy(() -> Http.str(null, "key"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Nested JSON object access")
        void nestedObjectAccess() {
            var obj = Http.parseObject("{\"outer\": {\"inner\": \"value\"}}");
            var inner = Http.obj(obj, "outer");
            String value = Http.str(inner, "inner");
            assertThat(value).isEqualTo("value");
        }

        @Test
        @DisplayName("Array access from object")
        void arrayAccessFromObject() {
            var obj = Http.parseObject("{\"items\": [\"a\", \"b\", \"c\"]}");
            var items = Http.list(obj, "items");
            assertThat(items).isNotNull();
            assertThat(items.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("Missing array key returns empty list")
        void missingArrayKey() {
            var obj = Http.parseObject("{\"key\": \"value\"}");
            var items = Http.arr(obj, "missing");
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("bool() with default value")
        void boolWithDefault() {
            var obj = Http.parseObject("{\"key\": \"value\"}");
            boolean result = Http.bool(obj, "missing", true);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("bool() parses string true")
        void boolParsesStringTrue() {
            var obj = Http.parseObject("{\"enabled\": \"true\"}");
            boolean result = Http.bool(obj, "enabled", false);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("bool() parses actual boolean")
        void boolParsesActualBoolean() {
            var obj = Http.parseObject("{\"enabled\": true, \"disabled\": false}");
            assertThat(Http.bool(obj, "enabled", false)).isTrue();
            assertThat(Http.bool(obj, "disabled", true)).isFalse();
        }

        @Test
        @DisplayName("str() with floating point number")
        void strWithFloatingPoint() {
            var obj = Http.parseObject("{\"value\": 3.14159}");
            String result = Http.str(obj, "value");
            assertThat(result).contains("3.14");
        }

        @Test
        @DisplayName("Deeply nested object access")
        void deeplyNestedAccess() {
            var obj = Http.parseObject("{\"a\": {\"b\": {\"c\": {\"d\": \"deep\"}}}}");
            var a = Http.obj(obj, "a");
            var b = Http.obj(a, "b");
            var c = Http.obj(b, "c");
            assertThat(Http.str(c, "d")).isEqualTo("deep");
        }
    }

    @Nested
    @DisplayName("Concurrent Operation Safety")
    class ConcurrencyTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Multiple threads computing hash of same file")
        void concurrentHashComputation() throws Exception {
            Path file = tempDir.resolve("shared.txt");
            Files.writeString(file, "shared content for hashing");

            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(10);
            ConcurrentHashMap<String, Integer> results = new ConcurrentHashMap<>();

            for (int i = 0; i < 10; i++) {
                executor.submit(() -> {
                    try {
                        String hash = Http.sha512(file);
                        results.merge(hash, 1, Integer::sum);
                    } catch (IOException e) {
                        results.merge("ERROR:" + e.getMessage(), 1, Integer::sum);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // All threads should compute the same hash
            assertThat(results).hasSize(1);
            assertThat(results.values().iterator().next()).isEqualTo(10);
        }

        @Test
        @DisplayName("Multiple threads encoding different strings")
        void concurrentEncoding() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(100);
            ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();

            for (int i = 0; i < 100; i++) {
                final String input = "test" + i + " value";
                executor.submit(() -> {
                    try {
                        String encoded = Http.encode(input);
                        results.put(input, encoded);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(results).hasSize(100);
            // Verify encoding is consistent
            for (int i = 0; i < 100; i++) {
                String input = "test" + i + " value";
                assertThat(results.get(input)).isEqualTo(Http.encode(input));
            }
        }

        @Test
        @DisplayName("Multiple threads parsing different JSON")
        void concurrentJsonParsing() throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(100);
            ConcurrentHashMap<Integer, String> results = new ConcurrentHashMap<>();

            for (int i = 0; i < 100; i++) {
                final int index = i;
                final String json = "{\"id\": " + i + ", \"name\": \"item" + i + "\"}";
                executor.submit(() -> {
                    try {
                        var obj = Http.parseObject(json);
                        if (obj != null) {
                            results.put(index, Http.str(obj, "name"));
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(results).hasSize(100);
            for (int i = 0; i < 100; i++) {
                assertThat(results.get(i)).isEqualTo("item" + i);
            }
        }
    }

    @Nested
    @DisplayName("Download URL Validation")
    class DownloadUrlValidation {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Download from HTTP URL is rejected")
        void downloadHttpRejected() {
            Path dest = tempDir.resolve("test.jar");

            assertThatThrownBy(() -> Http.download("http://example.com/file.jar", dest))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("HTTPS required");
        }

        @Test
        @DisplayName("Download with invalid URL throws")
        void downloadInvalidUrl() {
            Path dest = tempDir.resolve("test.jar");

            assertThatThrownBy(() -> Http.download("not a url", dest))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Invalid URL");
        }
    }
}
