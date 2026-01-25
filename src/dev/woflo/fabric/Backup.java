package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

public class Backup {
    public static Path create(Path serverDir, Loader loader, String reason, Console console) {
        console.checking("Backup");
        Path backupDir = serverDir.resolve("woflo").resolve("backups").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "_" + reason);
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
            for (Path tmp : renamed) { try { Path orig = tmp.resolveSibling(tmp.getFileName().toString().replace(".old", "")); if (!Files.exists(orig)) Files.move(tmp, orig); } catch (IOException e2) {} }
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
        } catch (IOException e) {}
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
                walk.sorted(Comparator.reverseOrder()).forEach(f -> { try { Files.delete(f); } catch (IOException e) {} });
            }
        } else Files.deleteIfExists(p);
    }
}
