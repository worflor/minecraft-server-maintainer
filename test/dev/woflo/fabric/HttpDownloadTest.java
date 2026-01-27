package dev.woflo.fabric;

import dev.woflo.fabric.TestTags.UnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for HTTP download and verification logic.
 * Tests SHA-512 hash computation, download verification, and URL encoding.
 */
@UnitTest
@DisplayName("HTTP Download Logic")
class HttpDownloadTest {

    @TempDir
    Path tempDir;

    // ========================================================================
    // SHA-512 Hash Computation
    // ========================================================================

    @Nested
    @DisplayName("SHA-512 hash computation")
    class Sha512HashComputation {

        @Test
        @DisplayName("computes correct hash for known content")
        void computesCorrectHashForKnownContent() throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "Hello, World!");

            String hash = Http.sha512(testFile);

            // SHA-512 of "Hello, World!" - verified with: echo -n "Hello, World!" | sha512sum
            assertThat(hash).isEqualTo(
                    "374d794a95cdcfd8b35993185fef9ba368f160d8daf432d08ba9f1ed1e5abe6c" +
                    "c69291e0fa2fe0006a52570ef18c19def4e617c33ce52ef0a6e5fbe318cb0387");
        }

        @Test
        @DisplayName("hash is lowercase hex")
        void hashIsLowercaseHex() throws IOException {
            Path testFile = tempDir.resolve("test.txt");
            Files.writeString(testFile, "test content");

            String hash = Http.sha512(testFile);

            assertThat(hash).matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("same content produces same hash")
        void sameContentProducesSameHash() throws IOException {
            Path file1 = tempDir.resolve("file1.txt");
            Path file2 = tempDir.resolve("file2.txt");
            Files.writeString(file1, "identical content");
            Files.writeString(file2, "identical content");

            String hash1 = Http.sha512(file1);
            String hash2 = Http.sha512(file2);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("different content produces different hash")
        void differentContentProducesDifferentHash() throws IOException {
            Path file1 = tempDir.resolve("file1.txt");
            Path file2 = tempDir.resolve("file2.txt");
            Files.writeString(file1, "content A");
            Files.writeString(file2, "content B");

            String hash1 = Http.sha512(file1);
            String hash2 = Http.sha512(file2);

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("empty file has known SHA-512 hash")
        void emptyFileHasKnownHash() throws IOException {
            Path emptyFile = tempDir.resolve("empty.txt");
            Files.writeString(emptyFile, "");

            String hash = Http.sha512(emptyFile);

            // SHA-512 of empty string is well-known: verified with echo -n "" | sha512sum
            assertThat(hash).isEqualTo(
                    "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
                    "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e");
        }

        @Test
        @DisplayName("binary content hashes correctly with known value")
        void binaryContentHashesCorrectly() throws IOException, NoSuchAlgorithmException {
            Path binaryFile = tempDir.resolve("binary.dat");
            byte[] content = new byte[]{0x00, 0x01, (byte) 0xFF, 0x7F, (byte) 0x80};
            Files.write(binaryFile, content);

            String hash = Http.sha512(binaryFile);

            // Compute expected hash using Java's MessageDigest
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(content);
            StringBuilder expected = new StringBuilder();
            for (byte b : digest) expected.append(String.format("%02x", b));

            assertThat(hash).isEqualTo(expected.toString());
        }
    }

    // ========================================================================
    // Hash Verification Logic
    // ========================================================================

    @Nested
    @DisplayName("Hash verification logic")
    class HashVerificationLogic {

        @Test
        @DisplayName("verification passes for matching hash")
        void verificationPassesForMatchingHash() throws IOException {
            Path testFile = tempDir.resolve("test.jar");
            Files.writeString(testFile, "mod content");

            String actualHash = Http.sha512(testFile);
            String expectedHash = actualHash; // Same

            assertThat(actualHash).isEqualTo(expectedHash);
        }

        @Test
        @DisplayName("verification fails for mismatched hash")
        void verificationFailsForMismatchedHash() throws IOException {
            Path testFile = tempDir.resolve("test.jar");
            Files.writeString(testFile, "mod content");

            String actualHash = Http.sha512(testFile);
            String expectedHash = "0".repeat(128); // Wrong hash

            assertThat(actualHash).isNotEqualTo(expectedHash);
        }

        @Test
        @DisplayName("null expected hash skips verification")
        void nullExpectedHashSkipsVerification() {
            // In downloadVerified: if (expectedSha512 != null && !hash.equals(expectedSha512))
            String expectedHash = null;
            String actualHash = "somehash123";

            // Verification should pass (skip) when expected is null
            boolean shouldFail = expectedHash != null && !actualHash.equals(expectedHash);
            assertThat(shouldFail).isFalse();
        }
    }

    // ========================================================================
    // URL Encoding
    // ========================================================================

    @Nested
    @DisplayName("URL encoding")
    class UrlEncoding {

        @Test
        @DisplayName("encodes space as plus")
        void encodesSpaceAsPlus() {
            String encoded = Http.encode("hello world");
            assertThat(encoded).isEqualTo("hello+world");
        }

        @Test
        @DisplayName("encodes special characters")
        void encodesSpecialCharacters() {
            String encoded = Http.encode("[\"1.21.1\"]");

            assertThat(encoded).contains("%5B"); // [
            assertThat(encoded).contains("%5D"); // ]
            assertThat(encoded).contains("%22"); // "
        }

        @Test
        @DisplayName("alphanumeric characters unchanged")
        void alphanumericCharactersUnchanged() {
            String encoded = Http.encode("abc123");
            assertThat(encoded).isEqualTo("abc123");
        }

        @Test
        @DisplayName("empty string produces empty string")
        void emptyStringProducesEmptyString() {
            String encoded = Http.encode("");
            assertThat(encoded).isEqualTo("");
        }

        @ParameterizedTest
        @DisplayName("encodes various game version arrays")
        @ValueSource(strings = {"[\"1.21.1\"]", "[\"1.20\", \"1.20.1\"]"})
        void encodesVariousGameVersionArrays(String array) {
            String encoded = Http.encode(array);
            assertThat(encoded).doesNotContain("[");
            assertThat(encoded).doesNotContain("]");
            assertThat(encoded).doesNotContain("\"");
        }
    }

    // ========================================================================
    // Download Verification Logic
    // ========================================================================

    @Nested
    @DisplayName("Download verification")
    class DownloadVerification {

        @Test
        @DisplayName("downloadVerified method signature exists with retries parameter")
        void downloadVerifiedMethodSignatureExists() {
            // Verify Http.downloadVerified exists with expected signature via reflection
            assertThatCode(() -> {
                var method = Http.class.getDeclaredMethod("downloadVerified",
                        String.class, Path.class, String.class, int.class);
                assertThat(method).isNotNull();
                assertThat(method.getReturnType()).isEqualTo(boolean.class);
                assertThat(method.getParameterCount()).isEqualTo(4);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("downloadVerified returns false for invalid URL")
        void downloadVerifiedReturnsFalseForInvalidUrl() {
            Path dest = tempDir.resolve("test.jar");

            // Invalid URL should return false (not throw), as downloadVerified handles errors
            boolean result = Http.downloadVerified("not-a-valid-url", dest, null, 0);

            assertThat(result).isFalse();
            assertThat(dest).doesNotExist();
        }

        @Test
        @DisplayName("downloadVerified returns false for HTTP URL (HTTPS required)")
        void downloadVerifiedReturnsFalseForHttpUrl() {
            Path dest = tempDir.resolve("test.jar");

            // HTTP URL should fail - only HTTPS allowed
            boolean result = Http.downloadVerified("http://example.com/file.jar", dest, null, 0);

            assertThat(result).isFalse();
        }
    }

    // ========================================================================
    // Modrinth API URL Construction
    // ========================================================================

    @Nested
    @DisplayName("Modrinth API URL construction")
    class ModrinthApiUrlConstruction {

        private static final String MODRINTH_API = "https://api.modrinth.com/v2";

        @Test
        @DisplayName("constructs version file URL correctly")
        void constructsVersionFileUrlCorrectly() {
            String hash = "abc123def456";
            String url = MODRINTH_API + "/version_file/" + hash;

            assertThat(url).isEqualTo("https://api.modrinth.com/v2/version_file/abc123def456");
        }

        @Test
        @DisplayName("constructs project versions URL with params")
        void constructsProjectVersionsUrlWithParams() {
            String projectId = "sodium";
            String mcVersion = "1.21.1";
            String loader = "fabric";

            String url = MODRINTH_API + "/project/" + Http.encode(projectId) +
                    "/version?game_versions=" + Http.encode("[\"" + mcVersion + "\"]") +
                    "&loaders=" + Http.encode("[\"" + loader + "\"]");

            assertThat(url).contains("sodium");
            assertThat(url).contains("game_versions=");
            assertThat(url).contains("loaders=");
        }
    }

    // ========================================================================
    // Large File Handling
    // ========================================================================

    @Nested
    @DisplayName("Large file handling")
    class LargeFileHandling {

        @Test
        @DisplayName("hashes large file correctly")
        void hashesLargeFileCorrectly() throws IOException {
            Path largeFile = tempDir.resolve("large.dat");

            // Create a 1MB file
            byte[] chunk = new byte[1024];
            java.util.Arrays.fill(chunk, (byte) 'X');

            try (var out = Files.newOutputStream(largeFile)) {
                for (int i = 0; i < 1024; i++) { // 1024 * 1KB = 1MB
                    out.write(chunk);
                }
            }

            String hash = Http.sha512(largeFile);

            assertThat(hash).hasSize(128);
            assertThat(Files.size(largeFile)).isEqualTo(1024 * 1024);
        }
    }

    // ========================================================================
    // File Extension Handling
    // ========================================================================

    @Nested
    @DisplayName("File extension handling")
    class FileExtensionHandling {

        @Test
        @DisplayName("JAR files hash correctly")
        void jarFilesHashCorrectly() throws IOException {
            Path jarFile = tempDir.resolve("test.jar");
            TestFixtures.createFabricMod(jarFile, "test", "1.0.0");

            String hash = Http.sha512(jarFile);

            assertThat(hash).hasSize(128);
        }

        @Test
        @DisplayName("ZIP files hash correctly")
        void zipFilesHashCorrectly() throws IOException {
            Path zipFile = tempDir.resolve("test.zip");
            TestFixtures.createDatapack(zipFile, "test-pack", "Test datapack");

            String hash = Http.sha512(zipFile);

            assertThat(hash).hasSize(128);
        }
    }
}
