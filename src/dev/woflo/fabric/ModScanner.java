package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class ModScanner {
    public record Mod(String id, Path path, String fileName) {}
    public record ScanConfig(String ext, String txt, String header) {}

    public static final ScanConfig MODS = new ScanConfig(".jar", "mods.txt", "# Server Maintainer - Mod List\n# Add # before a filename to skip updates\n\n");
    public static final ScanConfig PLUGINS = new ScanConfig(".jar", "plugins.txt", "# Server Maintainer - Plugin List\n# Add # before a filename to skip updates\n\n");
    public static final ScanConfig DATAPACKS = new ScanConfig(".zip", "datapacks.txt", "# Server Maintainer - Datapack List\n# Add # before a filename to skip updates\n\n");

    private static List<Path> listFiles(Path dir, String ext) throws IOException {
        try (var s = Files.list(dir)) { return s.filter(p -> p.toString().endsWith(ext)).toList(); }
    }

    public static List<Mod> scan(Path dir, Loader loader) throws IOException {
        if (!Files.exists(dir)) return List.of();
        Set<String> ignored = loader.ignoredModIds();
        List<Path> jars = listFiles(dir, ".jar");
        Set<String> skipped = readSkipped(dir, MODS);
        List<Mod> all;
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = jars.stream().map(j -> ex.submit(() -> {
                String id = loader.readModId(j);
                return (id != null && !id.isEmpty() && !ignored.contains(id.toLowerCase())) ? new Mod(id, j, j.getFileName().toString()) : null;
            })).toList();
            all = futures.stream().map(f -> { try { return f.get(); } catch (Exception e) { return null; } }).filter(Objects::nonNull).toList();
        }
        writeTxt(dir, MODS, all, skipped);
        return all.stream().filter(m -> !skipped.contains(m.fileName().toLowerCase())).toList();
    }

    public static List<Mod> scanPlugins(Path dir) throws IOException { return scanSimple(dir, PLUGINS); }
    public static List<Mod> scanDatapacks(Path dir) throws IOException { return scanSimple(dir, DATAPACKS); }

    private static List<Mod> scanSimple(Path dir, ScanConfig cfg) throws IOException {
        if (!Files.exists(dir)) return List.of();
        List<Path> files = listFiles(dir, cfg.ext);
        Set<String> skipped = readSkipped(dir, cfg);
        List<Mod> all = files.stream().map(f -> new Mod(f.getFileName().toString().replace(cfg.ext, ""), f, f.getFileName().toString())).toList();
        writeTxt(dir, cfg, all, skipped);
        return all.stream().filter(m -> !skipped.contains(m.fileName().toLowerCase())).toList();
    }

    private static Set<String> readSkipped(Path dir, ScanConfig cfg) {
        Path f = dir.resolve(cfg.txt); if (!Files.exists(f)) return Set.of();
        try {
            Set<String> s = new HashSet<>();
            for (String l : Files.readAllLines(f)) { l = l.trim(); if (l.startsWith("#") && l.endsWith(cfg.ext)) s.add(l.replaceFirst("^#\\s*", "").toLowerCase()); }
            return s;
        } catch (IOException e) { return Set.of(); }
    }

    private static void writeTxt(Path dir, ScanConfig cfg, List<Mod> items, Set<String> skipped) {
        try {
            StringBuilder sb = new StringBuilder(cfg.header);
            items.stream().sorted(Comparator.comparing(Mod::fileName)).forEach(m -> sb.append(skipped.contains(m.fileName().toLowerCase()) ? "# " : "").append(m.fileName()).append("\n"));
            Files.writeString(dir.resolve(cfg.txt), sb.toString());
        } catch (IOException e) {}
    }
}
