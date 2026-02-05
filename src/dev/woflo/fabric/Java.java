package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Finds the correct Java executable for a given Minecraft version.
 */
public class Java {

    // MC version -> minimum Java version required
    private static int requiredJavaVersion(String mcVersion) {
        if (mcVersion == null || mcVersion.equals("unknown")) return 21; // default to latest
        String[] parts = mcVersion.split("\\.");
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

        // 1.21+ requires Java 21
        if (minor >= 21) return 21;
        // 1.20.5+ requires Java 21
        if (minor == 20 && patch >= 5) return 21;
        // 1.18-1.20.4 requires Java 17
        if (minor >= 18) return 17;
        // 1.17 requires Java 16
        if (minor == 17) return 16;
        // 1.12-1.16 works with Java 8
        return 8;
    }

    /**
     * Finds the best Java executable for the given Minecraft version.
     * Returns the path to java executable, or "java" if nothing better found.
     */
    public static String findForMc(String mcVersion) {
        int required = requiredJavaVersion(mcVersion);
        String found = findJava(required);
        return found != null ? found : "java";
    }

    /**
     * Finds a Java installation with at least the given major version.
     * Prefers exact match, then closest higher version.
     */
    private static String findJava(int minVersion) {
        var candidates = scanJavaInstallations();

        // Sort by version, prefer exact match
        String best = null;
        int bestVersion = Integer.MAX_VALUE;

        for (var entry : candidates.entrySet()) {
            int version = entry.getValue();
            if (version >= minVersion && version < bestVersion) {
                best = entry.getKey();
                bestVersion = version;
            }
        }

        return best;
    }

    /**
     * Scans common locations for Java installations.
     * Returns map of java executable path -> major version.
     */
    private static Map<String, Integer> scanJavaInstallations() {
        var results = new HashMap<String, Integer>();
        String os = System.getProperty("os.name").toLowerCase();

        List<String> searchPaths = new ArrayList<>();

        if (os.contains("win")) {
            // Windows locations
            for (String base : new String[]{"C:\\Program Files\\", "C:\\Program Files (x86)\\"}) {
                searchPaths.add(base + "Java");
                searchPaths.add(base + "Eclipse Adoptium");
                searchPaths.add(base + "Microsoft");
                searchPaths.add(base + "Amazon Corretto");
                searchPaths.add(base + "Zulu");
                searchPaths.add(base + "BellSoft");
                searchPaths.add(base + "Semeru");
                searchPaths.add(base + "OpenJDK");
            }
        } else if (os.contains("mac")) {
            // macOS locations
            searchPaths.add("/Library/Java/JavaVirtualMachines");
            searchPaths.add(System.getProperty("user.home") + "/Library/Java/JavaVirtualMachines");
        } else {
            // Linux locations
            searchPaths.add("/usr/lib/jvm");
            searchPaths.add("/usr/java");
            searchPaths.add("/opt/java");
            String home = System.getProperty("user.home");
            searchPaths.add(home + "/.sdkman/candidates/java");
            searchPaths.add(home + "/.jdks");
        }

        for (String searchPath : searchPaths) {
            Path dir = Path.of(searchPath);
            if (!Files.isDirectory(dir)) continue;

            try (var stream = Files.list(dir)) {
                for (Path javaHome : stream.filter(Files::isDirectory).toList()) {
                    String javaBin = findJavaBinary(javaHome);
                    if (javaBin != null) {
                        int version = getJavaVersion(javaBin);
                        if (version > 0) {
                            results.put(javaBin, version);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }

        // Also check JAVA_HOME if set
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            String javaBin = findJavaBinary(Path.of(javaHome));
            if (javaBin != null) {
                int version = getJavaVersion(javaBin);
                if (version > 0) {
                    results.put(javaBin, version);
                }
            }
        }

        return results;
    }

    /**
     * Finds the java binary in a Java home directory.
     */
    private static String findJavaBinary(Path javaHome) {
        // Check standard locations
        for (String subpath : new String[]{"bin/java", "bin/java.exe", "Contents/Home/bin/java"}) {
            Path bin = javaHome.resolve(subpath);
            if (Files.isExecutable(bin)) {
                return bin.toString();
            }
        }
        return null;
    }

    /**
     * Gets the major version of a Java executable by running it.
     */
    private static int getJavaVersion(String javaBin) {
        try {
            Process p = new ProcessBuilder(javaBin, "-version")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (var is = p.getInputStream()) {
                output = new String(is.readAllBytes());
            }
            p.waitFor();

            // Parse version from output like: openjdk version "21.0.1" or java version "1.8.0_xxx"
            Matcher m = Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?").matcher(output);
            if (m.find()) {
                int major = Integer.parseInt(m.group(1));
                // Handle old 1.x versioning (1.8 = 8)
                if (major == 1 && m.group(2) != null) {
                    major = Integer.parseInt(m.group(2));
                }
                return major;
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
