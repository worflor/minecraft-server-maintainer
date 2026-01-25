package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

public class Backup {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int CRASH_REPORTS_KEEP = 5;

    private static FileTime mtime(Path p) { try { return Files.getLastModifiedTime(p); } catch (IOException e) { return FileTime.fromMillis(0); } }
    private static Path stasisDir(Path serverDir) { return serverDir.resolve("woflo").resolve("stasis"); }

    public static Path create(Path serverDir, Loader loader, String reason, Console console) {
        console.checking("Backup");
        Path backupDir = serverDir.resolve("woflo").resolve("backups").resolve(LocalDateTime.now().format(TIMESTAMP) + "_" + reason);
        try {
            Files.createDirectories(backupDir);
            for (String item : loader.backupItems()) {
                Path src = serverDir.resolve(item);
                if (Files.exists(src)) copy(src, backupDir.resolve(item));
            }
            console.checkDone("ready", true);
            return backupDir;
        } catch (IOException e) { console.fail("Backup failed: " + e.getMessage()); return null; }
    }

    public static boolean restore(Path backupDir, Path serverDir, Loader loader, Console console) {
        console.warn("Restoring backup...");
        java.util.List<Path> renamed = new java.util.ArrayList<>();
        try {
            for (String item : loader.backupItems()) {
                Path src = backupDir.resolve(item), dest = serverDir.resolve(item);
                if (Files.exists(src)) {
                    if (Files.exists(dest)) { Path tmp = dest.resolveSibling(dest.getFileName() + ".old"); Files.move(dest, tmp, StandardCopyOption.REPLACE_EXISTING); renamed.add(tmp); }
                    copy(src, dest);
                }
            }
            for (Path tmp : renamed) del(tmp);
            console.info("Restored from backup");
            return true;
        } catch (IOException e) {
            for (Path tmp : renamed) { try { String name = tmp.getFileName().toString(); Path orig = tmp.resolveSibling(name.endsWith(".old") ? name.substring(0, name.length() - 4) : name); if (!Files.exists(orig)) Files.move(tmp, orig); } catch (IOException e2) {} }
            console.fail("Restore failed: " + e.getMessage()); return false;
        }
    }

    public static void cleanup(Path serverDir, int keepDays) {
        Path dir = serverDir.resolve("woflo").resolve("backups");
        if (!Files.exists(dir)) return;
        Instant cutoff = Instant.now().minus(Duration.ofDays(keepDays));
        try (var s = Files.list(dir)) {
            s.filter(Files::isDirectory).forEach(d -> {
                try { if (Files.getLastModifiedTime(d).toInstant().isBefore(cutoff)) del(d); }
                catch (IOException e) {}
            });
        } catch (IOException ignored) {}
    }

    private static void copy(Path src, Path dest) throws IOException {
        if (Files.isDirectory(src)) {
            try (var walk = Files.walk(src)) {
                walk.forEach(s -> { try { Path t = dest.resolve(src.relativize(s)); if (Files.isDirectory(s)) Files.createDirectories(t); else Files.copy(s, t, StandardCopyOption.REPLACE_EXISTING); } catch (IOException e) { throw new UncheckedIOException(e); } });
            }
        } else Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void del(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (var walk = Files.walk(p)) {
                walk.sorted(Comparator.reverseOrder()).forEach(f -> { try { Files.delete(f); } catch (IOException ignored) {} });
            }
        } else Files.deleteIfExists(p);
    }

    private static final Set<String> STASIS_SKIP = Set.of("libraries", "versions", ".fabric", ".quilt", ".cache", "woflo/backups", "woflo/stasis");

    public static boolean stasisDue(Path serverDir, int intervalHours) {
        Path dir = stasisDir(serverDir);
        if (!Files.exists(dir)) return true;
        try (var s = Files.list(dir)) {
            var latest = s.filter(p -> p.toString().endsWith(".zip")).max(Comparator.comparing(Backup::mtime));
            return latest.isEmpty() || mtime(latest.get()).toInstant().plus(Duration.ofHours(intervalHours)).isBefore(Instant.now());
        } catch (IOException e) { return true; }
    }

    public static void createStasis(Path serverDir, Console console) {
        Path dest = stasisDir(serverDir).resolve(LocalDateTime.now().format(TIMESTAMP) + ".zip");
        try {
            Files.createDirectories(dest.getParent());
            Set<Path> recentCrashes = recentFiles(serverDir.resolve("crash-reports"), CRASH_REPORTS_KEEP);
            long[] size = {0};
            try (var zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(dest)))) {
                zos.setLevel(Deflater.BEST_COMPRESSION);
                try (var walk = Files.walk(serverDir)) {
                    walk.filter(Files::isRegularFile).filter(p -> !skipStasis(serverDir, p, recentCrashes)).forEach(p -> {
                        try {
                            zos.putNextEntry(new ZipEntry(serverDir.relativize(p).toString().replace('\\', '/')));
                            Files.copy(p, zos);
                            zos.closeEntry();
                            size[0] += Files.size(p);
                            console.checking("Stasis... " + formatSize(size[0]));
                        } catch (IOException ignored) {}
                    });
                }
            }
            console.checkDone(formatSize(Files.size(dest)), true);
        } catch (IOException e) { console.fail("Stasis failed: " + e.getMessage()); }
    }

    private static boolean skipStasis(Path root, Path file, Set<Path> recentCrashes) {
        String rel = root.relativize(file).toString().replace('\\', '/').toLowerCase();
        String first = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : rel;
        for (String skip : STASIS_SKIP) if (rel.startsWith(skip) || first.equals(skip)) return true;
        if (first.equals("logs")) return !rel.endsWith("/latest.log") && !rel.equals("latest.log");
        if (first.equals("crash-reports")) return !recentCrashes.contains(file);
        return false;
    }

    private static Set<Path> recentFiles(Path dir, int keep) {
        if (!Files.exists(dir)) return Set.of();
        try (var s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).sorted(Comparator.comparing(Backup::mtime).reversed()).limit(keep).collect(java.util.stream.Collectors.toSet());
        } catch (IOException e) { return Set.of(); }
    }

    public static void cleanupStasis(Path serverDir, int keep) {
        Path dir = stasisDir(serverDir);
        if (!Files.exists(dir)) return;
        try (var s = Files.list(dir)) {
            var files = s.filter(p -> p.toString().endsWith(".zip")).sorted(Comparator.comparing(Backup::mtime).reversed()).skip(keep).toList();
            for (Path f : files) Files.deleteIfExists(f);
        } catch (IOException ignored) {}
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        return String.format("%.0f %sB", bytes / Math.pow(1024, exp), "KMGT".charAt(exp - 1));
    }
}
