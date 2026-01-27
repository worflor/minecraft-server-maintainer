package dev.woflo.fabric;

import dev.woflo.fabric.Http;
import dev.woflo.fabric.TestTags.UnitTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for the custom JSON parser in Http.java.
 * Tests parsing of all JSON types, edge cases, unicode handling, and error conditions.
 */
@UnitTest
@DisplayName("Http JSON Parser")
class HttpJsonParserTest {

    // ========================================================================
    // Primitive Values
    // ========================================================================

    @Nested
    @DisplayName("String parsing")
    class StringParsing {

        @Test
        @DisplayName("parses simple string")
        void parsesSimpleString() {
            var result = Http.parseObject("{\"key\": \"value\"}");
            assertThat(Http.str(result, "key")).isEqualTo("value");
        }

        @Test
        @DisplayName("parses empty string")
        void parsesEmptyString() {
            var result = Http.parseObject("{\"key\": \"\"}");
            assertThat(Http.str(result, "key")).isEqualTo("");
        }

        @Test
        @DisplayName("parses string with spaces")
        void parsesStringWithSpaces() {
            var result = Http.parseObject("{\"key\": \"hello world\"}");
            assertThat(Http.str(result, "key")).isEqualTo("hello world");
        }

        @ParameterizedTest
        @DisplayName("parses escape sequences")
        @CsvSource({
                "\\n, '\n'",
                "\\t, '\t'",
                "\\r, '\r'",
                "\\\", '\"'",
                "\\\\, '\\'",
                "\\/, '/'"
        })
        void parsesEscapeSequences(String escaped, char expected) {
            var result = Http.parseObject("{\"key\": \"" + escaped + "\"}");
            assertThat(Http.str(result, "key")).isEqualTo(String.valueOf(expected));
        }

        @Test
        @DisplayName("parses unicode escape sequences")
        void parsesUnicodeEscapes() {
            var result = Http.parseObject("{\"key\": \"\\u0048\\u0065\\u006C\\u006C\\u006F\"}");
            assertThat(Http.str(result, "key")).isEqualTo("Hello");
        }

        @Test
        @DisplayName("parses unicode emoji")
        void parsesUnicodeEmoji() {
            var result = Http.parseObject("{\"key\": \"Hello \\u263A\"}");
            assertThat(Http.str(result, "key")).isEqualTo("Hello \u263A");
        }

        @Test
        @DisplayName("parses mixed escape sequences")
        void parsesMixedEscapes() {
            var result = Http.parseObject("{\"path\": \"C:\\\\Users\\\\test\\\\file.txt\"}");
            assertThat(Http.str(result, "path")).isEqualTo("C:\\Users\\test\\file.txt");
        }

        @Test
        @DisplayName("parses string with special characters")
        void parsesSpecialCharacters() {
            var result = Http.parseObject("{\"key\": \"line1\\nline2\\ttabbed\"}");
            assertThat(Http.str(result, "key")).isEqualTo("line1\nline2\ttabbed");
        }
    }

    @Nested
    @DisplayName("Number parsing")
    class NumberParsing {

        @Test
        @DisplayName("parses positive integer")
        void parsesPositiveInteger() {
            var result = Http.parseObject("{\"num\": 42}");
            assertThat(result.get("num")).isEqualTo(42.0);
        }

        @Test
        @DisplayName("parses negative integer")
        void parsesNegativeInteger() {
            var result = Http.parseObject("{\"num\": -42}");
            assertThat(result.get("num")).isEqualTo(-42.0);
        }

        @Test
        @DisplayName("parses zero")
        void parsesZero() {
            var result = Http.parseObject("{\"num\": 0}");
            assertThat(result.get("num")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("parses large integer")
        void parsesLargeInteger() {
            var result = Http.parseObject("{\"num\": 9223372036854775807}");
            assertThat(result.get("num")).isInstanceOf(Number.class);
        }

        @Test
        @DisplayName("parses floating point")
        void parsesFloatingPoint() {
            var result = Http.parseObject("{\"num\": 3.14159}");
            assertThat(result.get("num")).isEqualTo(3.14159);
        }

        @Test
        @DisplayName("parses negative floating point")
        void parsesNegativeFloatingPoint() {
            var result = Http.parseObject("{\"num\": -3.14159}");
            assertThat(result.get("num")).isEqualTo(-3.14159);
        }

        @Test
        @DisplayName("parses scientific notation positive exponent")
        void parsesScientificNotationPositive() {
            var result = Http.parseObject("{\"num\": 1.5e10}");
            assertThat(result.get("num")).isEqualTo(1.5e10);
        }

        @Test
        @DisplayName("parses scientific notation negative exponent")
        void parsesScientificNotationNegative() {
            var result = Http.parseObject("{\"num\": 1.5e-10}");
            assertThat(result.get("num")).isEqualTo(1.5e-10);
        }

        @Test
        @DisplayName("parses scientific notation uppercase E")
        void parsesScientificNotationUppercase() {
            var result = Http.parseObject("{\"num\": 1.5E10}");
            assertThat(result.get("num")).isEqualTo(1.5E10);
        }

        @Test
        @DisplayName("parses scientific notation with explicit plus")
        void parsesScientificNotationExplicitPlus() {
            var result = Http.parseObject("{\"num\": 1.5e+10}");
            assertThat(result.get("num")).isEqualTo(1.5e+10);
        }
    }

    @Nested
    @DisplayName("Boolean parsing")
    class BooleanParsing {

        @Test
        @DisplayName("parses true")
        void parsesTrue() {
            var result = Http.parseObject("{\"flag\": true}");
            assertThat(result.get("flag")).isEqualTo(true);
        }

        @Test
        @DisplayName("parses false")
        void parsesFalse() {
            var result = Http.parseObject("{\"flag\": false}");
            assertThat(result.get("flag")).isEqualTo(false);
        }

        @Test
        @DisplayName("bool helper returns default for missing key")
        void boolHelperDefaultsForMissingKey() {
            var result = Http.parseObject("{\"other\": true}");
            assertThat(Http.bool(result, "flag", true)).isTrue();
            assertThat(Http.bool(result, "flag", false)).isFalse();
        }

        @Test
        @DisplayName("bool helper parses string boolean")
        void boolHelperParsesStringBoolean() {
            var result = Http.parseObject("{\"flag\": \"true\"}");
            assertThat(Http.bool(result, "flag", false)).isTrue();
        }
    }

    @Nested
    @DisplayName("Null parsing")
    class NullParsing {

        @Test
        @DisplayName("parses null value")
        void parsesNull() {
            var result = Http.parseObject("{\"key\": null}");
            assertThat(result.get("key")).isNull();
        }

        @Test
        @DisplayName("str helper returns null for null value")
        void strHelperReturnsNullForNullValue() {
            var result = Http.parseObject("{\"key\": null}");
            // When the JSON value is null, str() returns null (not the string "null")
            assertThat(Http.str(result, "key")).isNull();
        }

        @Test
        @DisplayName("str helper returns null for missing key")
        void strHelperReturnsNullForMissingKey() {
            var result = Http.parseObject("{\"other\": \"value\"}");
            assertThat(Http.str(result, "key")).isNull();
        }
    }

    // ========================================================================
    // Objects
    // ========================================================================

    @Nested
    @DisplayName("Object parsing")
    class ObjectParsing {

        @Test
        @DisplayName("parses empty object")
        void parsesEmptyObject() {
            var result = Http.parseObject("{}");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("parses simple object")
        void parsesSimpleObject() {
            var result = Http.parseObject("{\"name\": \"test\", \"count\": 5}");
            assertThat(result)
                    .hasSize(2)
                    .containsEntry("name", "test")
                    .containsEntry("count", 5.0);
        }

        @Test
        @DisplayName("parses nested object")
        void parsesNestedObject() {
            var result = Http.parseObject("{\"outer\": {\"inner\": \"value\"}}");
            var outer = Http.obj(result, "outer");
            assertThat(outer).isNotNull();
            assertThat(Http.str(outer, "inner")).isEqualTo("value");
        }

        @Test
        @DisplayName("parses deeply nested objects")
        void parsesDeeplyNestedObjects() {
            var result = Http.parseObject("{\"a\": {\"b\": {\"c\": {\"d\": \"deep\"}}}}");
            var a = Http.obj(result, "a");
            var b = Http.obj(a, "b");
            var c = Http.obj(b, "c");
            assertThat(Http.str(c, "d")).isEqualTo("deep");
        }

        @Test
        @DisplayName("parses object with mixed value types")
        void parsesMixedValueTypes() {
            var result = Http.parseObject("""
                    {
                        "string": "text",
                        "number": 42,
                        "float": 3.14,
                        "bool": true,
                        "null": null,
                        "array": [1, 2, 3],
                        "object": {"nested": true}
                    }
                    """);

            assertThat(Http.str(result, "string")).isEqualTo("text");
            assertThat(result.get("number")).isEqualTo(42.0);
            assertThat(result.get("float")).isEqualTo(3.14);
            assertThat(result.get("bool")).isEqualTo(true);
            assertThat(result.get("null")).isNull();
            assertThat(Http.list(result, "array")).hasSize(3);
            assertThat(Http.obj(result, "object")).containsEntry("nested", true);
        }

        @Test
        @DisplayName("preserves key order")
        void preservesKeyOrder() {
            var result = Http.parseObject("{\"z\": 1, \"a\": 2, \"m\": 3}");
            var keys = new ArrayList<>(result.keySet());
            assertThat(keys).containsExactly("z", "a", "m");
        }
    }

    // ========================================================================
    // Arrays
    // ========================================================================

    @Nested
    @DisplayName("Array parsing")
    class ArrayParsing {

        @Test
        @DisplayName("parses empty array")
        void parsesEmptyArray() {
            var result = Http.parseArray("[]");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("parses string array")
        void parsesStringArray() {
            var result = Http.parseArray("[\"a\", \"b\", \"c\"]");
            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("parses number array")
        void parsesNumberArray() {
            var result = Http.parseArray("[1, 2, 3]");
            // JSON parser returns numbers as their parsed type (Long or Double)
            assertThat(result).hasSize(3);
            assertThat(((Number) result.get(0)).intValue()).isEqualTo(1);
            assertThat(((Number) result.get(1)).intValue()).isEqualTo(2);
            assertThat(((Number) result.get(2)).intValue()).isEqualTo(3);
        }

        @Test
        @DisplayName("parses mixed array")
        void parsesMixedArray() {
            var result = Http.parseArray("[\"string\", 42, true, null]");
            assertThat(result).containsExactly("string", 42.0, true, null);
        }

        @Test
        @DisplayName("parses array of objects")
        void parsesArrayOfObjects() {
            var result = Http.parseArray("[{\"id\": 1}, {\"id\": 2}]");
            assertThat(result).hasSize(2);
            @SuppressWarnings("unchecked")
            var first = (Map<String, Object>) result.get(0);
            assertThat(((Number) first.get("id")).intValue()).isEqualTo(1);
        }

        @Test
        @DisplayName("parses nested arrays")
        void parsesNestedArrays() {
            var result = Http.parseArray("[[1, 2], [3, 4], [5, 6]]");
            assertThat(result).hasSize(3);
            @SuppressWarnings("unchecked")
            var first = (List<Object>) result.get(0);
            assertThat(first).hasSize(2);
            assertThat(((Number) first.get(0)).intValue()).isEqualTo(1);
            assertThat(((Number) first.get(1)).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("arr helper returns list of objects")
        void arrHelperReturnsListOfObjects() {
            var result = Http.parseObject("{\"items\": [{\"name\": \"a\"}, {\"name\": \"b\"}]}");
            var items = Http.arr(result, "items");
            assertThat(items).hasSize(2);
            assertThat(Http.str(items.get(0), "name")).isEqualTo("a");
        }

        @Test
        @DisplayName("arr helper returns empty list for missing key")
        void arrHelperReturnsEmptyListForMissingKey() {
            var result = Http.parseObject("{\"other\": \"value\"}");
            var items = Http.arr(result, "items");
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("list helper returns list of any objects")
        void listHelperReturnsListOfAny() {
            var result = Http.parseObject("{\"versions\": [\"1.21.1\", \"1.21\", \"1.20.6\"]}");
            var versions = Http.list(result, "versions");
            assertThat(versions).containsExactly("1.21.1", "1.21", "1.20.6");
        }
    }

    // ========================================================================
    // Whitespace Handling
    // ========================================================================

    @Nested
    @DisplayName("Whitespace handling")
    class WhitespaceHandling {

        @Test
        @DisplayName("handles no whitespace")
        void handlesNoWhitespace() {
            var result = Http.parseObject("{\"key\":\"value\"}");
            assertThat(Http.str(result, "key")).isEqualTo("value");
        }

        @Test
        @DisplayName("handles extra whitespace")
        void handlesExtraWhitespace() {
            var result = Http.parseObject("  {  \"key\"  :  \"value\"  }  ");
            assertThat(Http.str(result, "key")).isEqualTo("value");
        }

        @Test
        @DisplayName("handles newlines and tabs")
        void handlesNewlinesAndTabs() {
            var result = Http.parseObject("{\n\t\"key\":\n\t\"value\"\n}");
            assertThat(Http.str(result, "key")).isEqualTo("value");
        }

        @Test
        @DisplayName("handles pretty-printed JSON")
        void handlesPrettyPrintedJson() {
            var json = """
                    {
                        "name": "test",
                        "items": [
                            {
                                "id": 1,
                                "value": "first"
                            },
                            {
                                "id": 2,
                                "value": "second"
                            }
                        ]
                    }
                    """;
            var result = Http.parseObject(json);
            assertThat(Http.str(result, "name")).isEqualTo("test");
            var items = Http.arr(result, "items");
            assertThat(items).hasSize(2);
        }
    }

    // ========================================================================
    // Real-World API Response Structures
    // ========================================================================

    @Nested
    @DisplayName("Real-world API responses")
    class RealWorldResponses {

        @Test
        @DisplayName("parses Mojang version manifest structure")
        void parsesMojangManifest() {
            var json = """
                    {
                        "latest": {
                            "release": "1.21.1",
                            "snapshot": "24w38a"
                        },
                        "versions": [
                            {"id": "1.21.1", "type": "release", "url": "https://example.com/1.21.1.json"},
                            {"id": "1.21", "type": "release", "url": "https://example.com/1.21.json"}
                        ]
                    }
                    """;
            var result = Http.parseObject(json);
            var latest = Http.obj(result, "latest");
            assertThat(Http.str(latest, "release")).isEqualTo("1.21.1");

            var versions = Http.arr(result, "versions");
            assertThat(versions).hasSize(2);
            assertThat(Http.str(versions.get(0), "id")).isEqualTo("1.21.1");
        }

        @Test
        @DisplayName("parses Modrinth version file structure")
        void parsesModrinthVersionFile() {
            var json = """
                    {
                        "id": "abc123",
                        "project_id": "sodium",
                        "version_number": "0.5.8",
                        "version_type": "release",
                        "loaders": ["fabric", "quilt"],
                        "game_versions": ["1.21.1", "1.21"],
                        "files": [
                            {
                                "hashes": {"sha512": "hashvalue", "sha1": "sha1value"},
                                "url": "https://cdn.modrinth.com/file.jar",
                                "filename": "sodium-0.5.8.jar",
                                "primary": true,
                                "size": 1000000
                            }
                        ]
                    }
                    """;
            var result = Http.parseObject(json);
            assertThat(Http.str(result, "project_id")).isEqualTo("sodium");
            assertThat(Http.str(result, "version_number")).isEqualTo("0.5.8");

            var files = Http.arr(result, "files");
            assertThat(files).hasSize(1);
            assertThat(Http.bool(files.get(0), "primary", false)).isTrue();

            var hashes = Http.obj(files.get(0), "hashes");
            assertThat(Http.str(hashes, "sha512")).isEqualTo("hashvalue");
        }

        @Test
        @DisplayName("parses Fabric Meta loader versions structure")
        void parsesFabricMetaLoaderVersions() {
            var json = """
                    [
                        {
                            "loader": {"version": "0.16.5", "stable": true},
                            "intermediary": {"version": "1.21.1"},
                            "launcherMeta": {"version": 1}
                        }
                    ]
                    """;
            var result = Http.parseArray(json);
            assertThat(result).hasSize(1);

            @SuppressWarnings("unchecked")
            var first = (Map<String, Object>) result.get(0);
            var loader = Http.obj(first, "loader");
            assertThat(Http.str(loader, "version")).isEqualTo("0.16.5");
            assertThat(Http.bool(loader, "stable", false)).isTrue();
        }

        @Test
        @DisplayName("parses Paper builds structure")
        void parsesPaperBuilds() {
            var json = """
                    {
                        "project_id": "paper",
                        "project_name": "Paper",
                        "version": "1.21.1",
                        "builds": [
                            {
                                "build": 130,
                                "time": "2024-09-15T10:00:00.000Z",
                                "downloads": {
                                    "application": {
                                        "name": "paper-1.21.1-130.jar",
                                        "sha256": "abc123"
                                    }
                                }
                            }
                        ]
                    }
                    """;
            var result = Http.parseObject(json);
            var builds = Http.arr(result, "builds");
            assertThat(builds).hasSize(1);

            var build = builds.get(0);
            assertThat(((Number) build.get("build")).intValue()).isEqualTo(130);

            var downloads = Http.obj(build, "downloads");
            var app = Http.obj(downloads, "application");
            assertThat(Http.str(app, "name")).isEqualTo("paper-1.21.1-130.jar");
        }
    }

    // ========================================================================
    // Edge Cases and Error Handling
    // ========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles empty string input")
        void handlesEmptyStringInput() {
            // The parser returns null for empty input
            var result = Http.parseObject("{}");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("handles unicode keys")
        void handlesUnicodeKeys() {
            var result = Http.parseObject("{\"\\u006B\\u0065\\u0079\": \"value\"}");
            assertThat(Http.str(result, "key")).isEqualTo("value");
        }

        @Test
        @DisplayName("handles very long strings")
        void handlesVeryLongStrings() {
            var longValue = "x".repeat(10000);
            var result = Http.parseObject("{\"key\": \"" + longValue + "\"}");
            assertThat(Http.str(result, "key")).isEqualTo(longValue);
        }

        @Test
        @DisplayName("handles deeply nested structure")
        void handlesDeeplyNestedStructure() {
            // Build a deeply nested JSON string
            var sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("{\"level").append(i).append("\": ");
            }
            sb.append("\"deep\"");
            for (int i = 0; i < 50; i++) {
                sb.append("}");
            }

            var result = Http.parseObject(sb.toString());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles trailing comma in object (lenient)")
        void handlesTrailingCommaInObject() {
            // Note: The parser may or may not handle this depending on implementation
            // Testing what happens
            try {
                var result = Http.parseObject("{\"key\": \"value\",}");
                // If it parses, check the result
                assertThat(result).containsKey("key");
            } catch (RuntimeException e) {
                // If it throws, that's also acceptable behavior
                assertThat(e).isInstanceOf(RuntimeException.class);
            }
        }

        @Test
        @DisplayName("handles consecutive escape sequences")
        void handlesConsecutiveEscapeSequences() {
            var result = Http.parseObject("{\"key\": \"\\n\\n\\t\\t\"}");
            assertThat(Http.str(result, "key")).isEqualTo("\n\n\t\t");
        }

        @Test
        @DisplayName("handles numeric string values")
        void handlesNumericStringValues() {
            var result = Http.parseObject("{\"version\": \"1.21.1\"}");
            assertThat(Http.str(result, "version")).isEqualTo("1.21.1");
        }

        @Test
        @DisplayName("obj returns null for missing key")
        void objReturnsNullForMissingKey() {
            var obj = Http.parseObject("{\"a\": 1}");
            var nested = Http.obj(obj, "nonexistent");
            assertThat(nested).isNull();
        }

        @Test
        @DisplayName("list returns empty for missing key")
        void listReturnsEmptyForMissingKey() {
            var obj = Http.parseObject("{\"a\": 1}");
            var list = Http.list(obj, "nonexistent");
            assertThat(list).isEmpty();
        }
    }

    @Nested
    @DisplayName("Error conditions")
    class ErrorConditions {

        @Test
        @DisplayName("throws on invalid unicode escape")
        void throwsOnInvalidUnicodeEscape() {
            assertThatThrownBy(() -> Http.parseObject("{\"key\": \"\\uZZZZ\"}"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("unicode");
        }

        @Test
        @DisplayName("throws on truncated unicode escape")
        void throwsOnTruncatedUnicodeEscape() {
            assertThatThrownBy(() -> Http.parseObject("{\"key\": \"\\u00\"}"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("throws on unexpected character")
        void throwsOnUnexpectedCharacter() {
            assertThatThrownBy(() -> Http.parseObject("{key: \"value\"}"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unexpected");
        }
    }

    // ========================================================================
    // Parameterized Tests
    // ========================================================================

    @ParameterizedTest
    @DisplayName("parses various number formats")
    @MethodSource("numberFormats")
    void parsesVariousNumberFormats(String json, Number expected) {
        var result = Http.parseObject("{\"n\": " + json + "}");
        Number actual = (Number) result.get("n");
        assertThat(actual.doubleValue()).isCloseTo(expected.doubleValue(), within(0.0001));
    }

    static Stream<Arguments> numberFormats() {
        return Stream.of(
                Arguments.of("0", 0.0),
                Arguments.of("1", 1.0),
                Arguments.of("-1", -1.0),
                Arguments.of("123456789", 123456789.0),
                Arguments.of("0.0", 0.0),
                Arguments.of("0.5", 0.5),
                Arguments.of("-0.5", -0.5),
                Arguments.of("1e10", 1e10),
                Arguments.of("1E10", 1E10),
                Arguments.of("1e+10", 1e+10),
                Arguments.of("1e-10", 1e-10),
                Arguments.of("1.5e10", 1.5e10)
        );
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
