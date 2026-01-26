package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.zip.*;

public class ModScanner {
    public record Mod(String id, Path path, String fileName) {}

    private static Path wofloDir;

    public static void init(Path woflo) {
        wofloDir = woflo;
        Path server = woflo.getParent();
        migrate(server.resolve("mods").resolve("mods.txt"), woflo.resolve("mods.txt"));
        migrate(server.resolve("plugins").resolve("plugins.txt"), woflo.resolve("plugins.txt"));
        migrate(server.resolve(worldName(server)).resolve("datapacks").resolve("datapacks.txt"), woflo.resolve("datapacks.txt"));
    }

    static String worldName(Path server) {
        try {
            for (String l : Files.readAllLines(server.resolve("server.properties")))
                if (l.startsWith("level-name=")) return l.substring(11).trim();
        } catch (IOException ignored) {}
        return "world";
    }

    private static void migrate(Path src, Path dest) {
        try { if (Files.exists(src) && !Files.exists(dest)) Files.move(src, dest); } catch (IOException ignored) {}
    }

    public static List<Mod> scan(Path dir, Loader loader) throws IOException {
        return scanDir(dir, ".jar", "mods.txt", "Mod", loader::readModId, loader.ignoredModIds());
    }

    public static List<Mod> scanPlugins(Path dir) throws IOException {
        return scanDir(dir, ".jar", "plugins.txt", "Plugin", ModScanner::readPluginId, Set.of());
    }

    public static List<Mod> scanDatapacks(Path dir) throws IOException {
        return scanDir(dir, ".zip", "datapacks.txt", "Datapack", ModScanner::readDatapackId, Set.of());
    }

    private static List<Mod> scanDir(Path dir, String ext, String skipFile, String label, Function<Path, String> reader, Set<String> ignored) throws IOException {
        if (!Files.exists(dir)) return List.of();
        List<Mod> all;
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = list(dir, ext).stream().map(p -> ex.submit(() -> {
                String id = reader.apply(p);
                return (id != null && !id.isEmpty() && !ignored.contains(id.toLowerCase()))
                        ? new Mod(id, p, p.getFileName().toString()) : null;
            })).toList();
            all = futures.stream().map(f -> { try { return f.get(); } catch (Exception e) { return null; } })
                    .filter(Objects::nonNull).toList();
        }
        return filter(all, skipFile, ext, label);
    }

    // Plugin: read from plugin.yml, bungee.yml, or velocity-plugin.json
    private static String readPluginId(Path jar) {
        try (var z = new ZipFile(jar.toFile())) {
            String id = yaml(z, "plugin.yml", "name");
            if (id == null) id = yaml(z, "bungee.yml", "name");
            if (id == null) id = json(z, "velocity-plugin.json", "id");
            return id;
        } catch (Exception e) { return null; }
    }

    // Datapack: read from modrinth.json, pack.mcmeta, or clean filename
    private static String readDatapackId(Path zip) {
        try (var z = new ZipFile(zip.toFile())) {
            String id = json(z, "modrinth.json", "project_id");
            if (id == null) {
                var pack = jsonObj(z, "pack.mcmeta");
                if (pack != null) id = Http.str(Http.obj(pack, "pack"), "id");
            }
            if (id != null) return id;
        } catch (Exception ignored) {}
        String name = zip.getFileName().toString().replace(".zip", "");
        return name.replaceAll("-?\\d+\\.\\d+.*$", "").replaceAll("[_-]$", "");
    }

    private static String yaml(ZipFile z, String file, String key) {
        var entry = z.getEntry(file);
        if (entry == null) return null;
        try (var r = new BufferedReader(new InputStreamReader(z.getInputStream(entry)))) {
            String prefix = key + ":";
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                if (line.startsWith(prefix)) {
                    String v = line.substring(prefix.length()).trim();
                    if (v.length() >= 2 && (v.charAt(0) == '"' || v.charAt(0) == '\''))
                        v = v.substring(1, v.length() - 1);
                    return v.isEmpty() ? null : v;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String json(ZipFile z, String file, String key) {
        var obj = jsonObj(z, file);
        String v = obj != null ? Http.str(obj, key) : null;
        return (v != null && !v.isEmpty()) ? v : null;
    }

    private static Map<String, Object> jsonObj(ZipFile z, String file) {
        var entry = z.getEntry(file);
        if (entry == null) return null;
        try (var is = z.getInputStream(entry)) { return Http.parseObject(new String(is.readAllBytes())); }
        catch (Exception e) { return null; }
    }

    private static List<Mod> filter(List<Mod> all, String file, String ext, String label) {
        if (wofloDir == null) return all;
        Path f = wofloDir.resolve(file);
        Set<String> skipped = loadSkipped(f, ext);
        writeIfChanged(f, genSkipFile(all, skipped, label));
        return all.stream().filter(m -> !skipped.contains(m.fileName().toLowerCase())).toList();
    }

    private static Set<String> loadSkipped(Path f, String ext) {
        if (!Files.exists(f)) return Set.of();
        try {
            Set<String> s = new HashSet<>();
            for (String l : Files.readAllLines(f))
                if ((l = l.trim()).startsWith("#") && l.toLowerCase().endsWith(ext))
                    s.add(l.substring(1).trim().toLowerCase());
            return s;
        } catch (IOException e) { return Set.of(); }
    }

    private static String genSkipFile(List<Mod> items, Set<String> skipped, String label) {
        var sb = new StringBuilder("# ").append(label).append(" List - add # before filename to skip updates\n\n");
        items.stream().sorted(Comparator.comparing(Mod::fileName)).forEach(m ->
                sb.append(skipped.contains(m.fileName().toLowerCase()) ? "# " : "").append(m.fileName()).append("\n"));
        return sb.toString();
    }

    private static void writeIfChanged(Path f, String content) {
        try {
            if (!content.equals(Files.exists(f) ? Files.readString(f) : "")) Files.writeString(f, content);
        } catch (IOException ignored) {}
    }

    private static List<Path> list(Path dir, String ext) throws IOException {
        try (var s = Files.list(dir)) { return s.filter(p -> p.toString().endsWith(ext)).toList(); }
    }
}
