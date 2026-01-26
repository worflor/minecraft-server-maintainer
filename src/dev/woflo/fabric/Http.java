package dev.woflo.fabric;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;

public class Http {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String VERSION = Http.class.getPackage().getImplementationVersion() != null ? Http.class.getPackage().getImplementationVersion() : "dev";
    private static final String USER_AGENT = "woflo/ServerMaintainer/" + VERSION + " (github.com/woflo/minecraft-server-maintainer)";
    private static final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

    public static String get(String url) throws IOException, InterruptedException { return get(url, 3); }

    private static HttpRequest req(String url, Duration t) throws IOException {
        URI uri;
        try { uri = URI.create(url); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid URL: " + url); }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("HTTPS required: " + url);
        return HttpRequest.newBuilder().uri(uri).header("User-Agent", USER_AGENT).timeout(t).GET().build();
    }

    private static String get(String url, int retries) throws IOException, InterruptedException {
        var res = client.send(req(url, TIMEOUT), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429 && retries > 0) {
            Thread.sleep(Math.min(parseRetryAfter(res), 60) * 1000L);
            return get(url, retries - 1);
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) throw new IOException("HTTP " + res.statusCode());
        return res.body();
    }

    public static Map<String, Object> getJson(String url) throws IOException, InterruptedException { return parseObject(get(url)); }
    public static List<Object> getJsonArray(String url) throws IOException, InterruptedException { return parseArray(get(url)); }

    public static void download(String url, Path dest) throws IOException, InterruptedException { download(url, dest, 3); }

    private static void download(String url, Path dest, int retries) throws IOException, InterruptedException {
        Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
        var res = client.send(req(url, Duration.ofSeconds(60)), HttpResponse.BodyHandlers.ofFile(tmp));
        if (res.statusCode() == 429 && retries > 0) {
            Files.deleteIfExists(tmp);
            Thread.sleep(Math.min(parseRetryAfter(res), 60) * 1000L);
            download(url, dest, retries - 1);
            return;
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) { Files.deleteIfExists(tmp); throw new IOException("HTTP " + res.statusCode()); }
        try { Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING); }
    }

    public static boolean downloadVerified(String url, Path dest, String hash, int retries) {
        Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
        for (int i = 0; i < retries; i++) {
            try {
                download(url, dest);
                if (hash != null && !hash.isEmpty()) {
                    if (sha512(dest).equalsIgnoreCase(hash)) return true;
                    Files.deleteIfExists(dest); // hash mismatch, retry
                } else {
                    return true; // no hash provided, accept (API limitation)
                }
            } catch (Exception e) {
                try { Files.deleteIfExists(dest); } catch (IOException ignored) {}
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
        return false;
    }

    public static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static int parseRetryAfter(HttpResponse<?> res) {
        return res.headers().firstValue("Retry-After").map(s -> { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 5; } }).orElse(5);
    }

    public static String sha512(Path file) throws IOException {
        try {
            var md = MessageDigest.getInstance("SHA-512");
            try (var is = Files.newInputStream(file)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }

    // === JSON Parser ===
    private String src; private int pos;
    private Http(String src) { this.src = src; }

    private static Object parse(String json) { return new Http(json).parseValue(); }
    @SuppressWarnings("unchecked") static Map<String, Object> parseObject(String json) { return (Map<String, Object>) parse(json); }
    @SuppressWarnings("unchecked") static List<Object> parseArray(String json) { return (List<Object>) parse(json); }

    private Object parseValue() {
        skip();
        if (pos >= src.length()) return null;
        char c = src.charAt(pos);
        if (c == '{') return parseObj();
        if (c == '[') return parseArr();
        if (c == '"') return parseStr();
        if (c == 't' && src.startsWith("true", pos)) { pos += 4; return true; }
        if (c == 'f' && src.startsWith("false", pos)) { pos += 5; return false; }
        if (c == 'n' && src.startsWith("null", pos)) { pos += 4; return null; }
        if (c == '-' || Character.isDigit(c)) return parseNum();
        throw new RuntimeException("Unexpected: " + c);
    }

    private Map<String, Object> parseObj() {
        Map<String, Object> m = new LinkedHashMap<>();
        pos++; skip();
        while (pos < src.length() && src.charAt(pos) != '}') {
            skip(); String k = parseStr(); skip(); pos++; skip();
            m.put(k, parseValue()); skip();
            if (pos < src.length() && src.charAt(pos) == ',') pos++; skip();
        }
        pos++; return m;
    }

    private List<Object> parseArr() {
        List<Object> l = new ArrayList<>();
        pos++; skip();
        while (pos < src.length() && src.charAt(pos) != ']') {
            l.add(parseValue()); skip();
            if (pos < src.length() && src.charAt(pos) == ',') pos++; skip();
        }
        pos++; return l;
    }

    private String parseStr() {
        pos++; StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') break;
            if (c == '\\' && pos < src.length()) {
                char e = src.charAt(pos++);
                sb.append(switch (e) {
                    case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r'; case '"' -> '"'; case '\\' -> '\\'; case '/' -> '/';
                    case 'u' -> {
                        if (pos + 4 > src.length()) throw new RuntimeException("Invalid unicode escape");
                        String h = src.substring(pos, pos + 4);
                        pos += 4;
                        try { yield (char) Integer.parseInt(h, 16); }
                        catch (NumberFormatException ex) { throw new RuntimeException("Invalid unicode escape: \\u" + h); }
                    }
                    default -> e;
                });
            } else sb.append(c);
        }
        return sb.toString();
    }

    private Number parseNum() {
        int s = pos; boolean f = false;
        if (src.charAt(pos) == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.') { f = true; pos++; while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++; }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            f = true; pos++; if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        String n = src.substring(s, pos);
        return f ? Double.parseDouble(n) : Long.parseLong(n);
    }

    private void skip() { while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++; }

    // JSON utilities
    public static String str(Map<String, Object> o, String k) { Object v = o.get(k); return v == null ? null : v.toString(); }
    public static boolean bool(Map<String, Object> o, String k, boolean d) { Object v = o.get(k); return v == null ? d : v instanceof Boolean ? (Boolean) v : Boolean.parseBoolean(v.toString()); }
    @SuppressWarnings("unchecked") public static Map<String, Object> obj(Map<String, Object> o, String k) { return (Map<String, Object>) o.get(k); }
    @SuppressWarnings("unchecked") public static List<Map<String, Object>> arr(Map<String, Object> o, String k) { Object v = o.get(k); return v == null ? List.of() : (List<Map<String, Object>>) v; }
    @SuppressWarnings("unchecked") public static List<Object> list(Map<String, Object> o, String k) { Object v = o.get(k); return v == null ? List.of() : (List<Object>) v; }
}
