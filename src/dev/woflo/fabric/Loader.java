package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public enum Loader {
    FABRIC, FORGE, NEOFORGE, QUILT, VANILLA;

    public static Loader detect(Path dir) {
        if (Files.exists(dir.resolve("fabric-server-launch.jar")) || Files.exists(dir.resolve(".fabric"))) return FABRIC;
        Path mods = dir.resolve("mods");
        if (Files.exists(mods)) {
            try (var s = Files.list(mods)) {
                for (Path p : s.filter(f -> f.toString().endsWith(".jar")).toList()) {
                    String d = detectFromMod(p);
                    var l = switch (d) { case "fabric" -> FABRIC; case "forge" -> FORGE; case "neoforge" -> NEOFORGE; case "quilt" -> QUILT; default -> (Loader) null; };
                    if (l != null) return l;
                }
            } catch (IOException e) {}
        }
        try (var s = Files.list(dir)) { if (s.anyMatch(p -> p.getFileName().toString().toLowerCase().contains("forge") && p.toString().endsWith(".jar"))) return FORGE; } catch (IOException e) {}
        if (Files.exists(dir.resolve("server.jar")) && (!Files.exists(mods) || isEmpty(mods))) return VANILLA;
        return FABRIC;
    }

    private static boolean isEmpty(Path d) { try (var s = Files.list(d)) { return s.findFirst().isEmpty(); } catch (IOException e) { return true; } }

    private static String detectFromMod(Path jar) {
        try (var z = new ZipFile(jar.toFile())) {
            if (z.getEntry("fabric.mod.json") != null) return "fabric";
            if (z.getEntry("quilt.mod.json") != null) return "quilt";
            if (z.getEntry("META-INF/mods.toml") != null) return "forge";
            if (z.getEntry("META-INF/neoforge.mods.toml") != null) return "neoforge";
        } catch (IOException e) {}
        return null;
    }

    public String serverJar() { return switch (this) { case FABRIC -> "fabric-server-launch.jar"; case QUILT -> "quilt-server-launch.jar"; case FORGE -> "forge-server.jar"; case NEOFORGE -> "neoforge-server.jar"; case VANILLA -> "server.jar"; }; }

    public String findServerJar(Path dir) {
        String expected = serverJar();
        if (Files.exists(dir.resolve(expected))) return expected;
        // fallback: search by name
        String search = switch (this) { case FABRIC -> "fabric"; case QUILT -> "quilt"; case FORGE -> "forge"; case NEOFORGE -> "neoforge"; case VANILLA -> "server"; };
        try (var s = Files.list(dir)) {
            var jars = s.filter(p -> p.toString().endsWith(".jar"))
                .map(p -> p.getFileName().toString())
                .filter(n -> n.toLowerCase().contains(search))
                .filter(n -> !n.contains("installer"))
                .toList();
            if (jars.size() == 1) return jars.get(0);
            if (jars.size() > 1) throw new RuntimeException("Too many server JARs! I'm getting confused. Set 'server-jar' in config.yml or remove the extras to help me out:\n  - " + String.join("\n  - ", jars));
        } catch (IOException e) {}
        // Forge/NeoForge run scripts
        if ((this == FORGE || this == NEOFORGE) && (Files.exists(dir.resolve("run.bat")) || Files.exists(dir.resolve("run.sh")))) {
            return System.getProperty("os.name").toLowerCase().contains("win") ? "run.bat" : "run.sh";
        }
        return expected;
    }
    public String displayName() { return switch (this) { case FABRIC -> "Fabric"; case FORGE -> "Forge"; case NEOFORGE -> "NeoForge"; case QUILT -> "Quilt"; case VANILLA -> "Vanilla"; }; }
    public List<String> modrinthLoaders() { return switch (this) { case FABRIC -> List.of("fabric", "quilt"); case QUILT -> List.of("quilt", "fabric"); case NEOFORGE -> List.of("neoforge", "forge"); case FORGE -> List.of("forge", "neoforge"); case VANILLA -> List.of(); }; }
    public Set<String> ignoredModIds() { return switch (this) { case FABRIC -> Set.of("java", "minecraft", "fabricloader", "mixinextras", "fabric-api"); case QUILT -> Set.of("java", "minecraft", "quilt_loader", "quilted_fabric_api"); case FORGE -> Set.of("minecraft", "forge"); case NEOFORGE -> Set.of("minecraft", "neoforge"); case VANILLA -> Set.of(); }; }
    public String[] backupItems() { return switch (this) { case FABRIC -> new String[]{"mods", "versions", "libraries", "fabric-server-launch.jar", "current_version.txt"}; case QUILT -> new String[]{"mods", "versions", "libraries", "quilt-server-launch.jar", "current_version.txt"}; case FORGE, NEOFORGE -> new String[]{"mods", "libraries", "run.jar", "current_version.txt"}; case VANILLA -> new String[]{"server.jar", "current_version.txt"}; }; }

    public boolean isReady(String mc) {
        try { return switch (this) {
            case FABRIC -> !Http.getJsonArray("https://meta.fabricmc.net/v2/versions/loader/" + mc).isEmpty();
            case QUILT -> !Http.getJsonArray("https://meta.quiltmc.org/v3/versions/loader/" + mc).isEmpty();
            case FORGE -> Http.obj(Http.getJson("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"), "promos").containsKey(mc + "-latest");
            case NEOFORGE -> { String[] p = mc.split("\\."); String pfx = (p.length >= 2 ? p[1] : "21") + "." + (p.length >= 3 ? p[2] : "0") + "."; yield Http.list(Http.getJson("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"), "versions").stream().anyMatch(v -> v.toString().startsWith(pfx)); }
            case VANILLA -> true;
        }; } catch (Exception e) { return false; }
    }

    @SuppressWarnings("unchecked")
    public String getLatestSupported(boolean allowSnapshots) {
        try {
            String url = switch (this) { case FABRIC -> "https://meta.fabricmc.net/v2/versions/game"; case QUILT -> "https://meta.quiltmc.org/v3/versions/game"; default -> null; };
            if (url != null) {
                for (Object o : Http.getJsonArray(url)) {
                    var g = (Map<String, Object>) o;
                    if (allowSnapshots || Http.bool(g, "stable", false)) return Http.str(g, "version");
                }
                return null;
            }
            var latest = Http.obj(Http.getJson("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"), "latest");
            return Http.str(latest, allowSnapshots ? "snapshot" : "release");
        } catch (Exception e) { return null; }
    }

    public boolean install(String mc, Path dir, Console c) {
        try { return switch (this) {
            case FABRIC -> runInstaller(Http.getJsonArray("https://meta.fabricmc.net/v2/versions/installer"), dir, c, "java", "-jar", "installer.jar", "server", "-mcversion", mc, "-downloadMinecraft");
            case QUILT -> runInstaller(Http.getJsonArray("https://meta.quiltmc.org/v3/versions/installer"), dir, c, "java", "-jar", "installer.jar", "install", "server", mc, "--download-server");
            case FORGE -> installForge(mc, dir, c);
            case NEOFORGE -> installNeoForge(mc, dir, c);
            case VANILLA -> installVanilla(mc, dir, c);
        }; } catch (Exception e) { c.fail("Install failed: " + e.getMessage()); return false; }
    }

    @SuppressWarnings("unchecked")
    private boolean runInstaller(List<Object> list, Path dir, Console c, String... cmd) throws Exception {
        if (list == null || list.isEmpty()) { c.fail("Cannot get installer"); return false; }
        String url = Http.str((Map<String, Object>) list.getFirst(), "url");
        Path installer = dir.resolve("installer.jar");
        Http.download(url, installer);
        String[] full = Arrays.stream(cmd).map(s -> s.equals("installer.jar") ? installer.toString() : s).toArray(String[]::new);
        int exit = new ProcessBuilder(full).directory(dir.toFile()).inheritIO().start().waitFor();
        Files.deleteIfExists(installer);
        return exit == 0;
    }

    private boolean installForge(String mc, Path dir, Console c) throws Exception {
        var promos = Http.obj(Http.getJson("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"), "promos");
        String fv = Http.str(promos, mc + "-recommended"); if (fv == null) fv = Http.str(promos, mc + "-latest");
        if (fv == null) { c.fail("No Forge for MC " + mc); return false; }
        String full = mc + "-" + fv;
        return runInstaller(List.of(Map.of("url", "https://maven.minecraftforge.net/net/minecraftforge/forge/" + full + "/forge-" + full + "-installer.jar")), dir, c, "java", "-jar", "installer.jar", "--installServer");
    }

    private boolean installNeoForge(String mc, Path dir, Console c) throws Exception {
        String[] p = mc.split("\\."); String pfx = (p.length >= 2 ? p[1] : "21") + "." + (p.length >= 3 ? p[2] : "0") + ".";
        var vers = Http.list(Http.getJson("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"), "versions");
        String nv = null;
        for (int i = vers.size() - 1; i >= 0; i--) { String v = vers.get(i).toString(); if (v.startsWith(pfx) && !v.contains("-beta") && !v.contains("-alpha")) { nv = v; break; } }
        if (nv == null) for (int i = vers.size() - 1; i >= 0; i--) { String v = vers.get(i).toString(); if (v.startsWith(pfx)) { nv = v; break; } }
        if (nv == null) { c.fail("No NeoForge for MC " + mc); return false; }
        return runInstaller(List.of(Map.of("url", "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + nv + "/neoforge-" + nv + "-installer.jar")), dir, c, "java", "-jar", "installer.jar", "--installServer");
    }

    @SuppressWarnings("unchecked")
    private boolean installVanilla(String mc, Path dir, Console c) throws Exception {
        var manifest = Http.getJson("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
        String vUrl = null; for (var v : Http.list(manifest, "versions")) { var m = (Map<String, Object>) v; if (mc.equals(Http.str(m, "id"))) { vUrl = Http.str(m, "url"); break; } }
        if (vUrl == null) { c.fail("Version " + mc + " not found"); return false; }
        Http.download(Http.str(Http.obj(Http.obj(Http.getJson(vUrl), "downloads"), "server"), "url"), dir.resolve("server.jar"));
        return true;
    }

    public String readModId(Path jar) {
        try (var z = new ZipFile(jar.toFile())) {
            return switch (this) {
                case FABRIC -> readEntry(z, "fabric.mod.json", j -> Http.str(Http.parseObject(j), "id"));
                case QUILT -> { String id = readEntry(z, "quilt.mod.json", j -> Http.str(Http.obj(Http.parseObject(j), "quilt_loader"), "id")); yield id != null ? id : readEntry(z, "fabric.mod.json", j -> Http.str(Http.parseObject(j), "id")); }
                case FORGE, NEOFORGE -> readEntry(z, z.getEntry("META-INF/neoforge.mods.toml") != null ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml", this::parseTomlModId);
                case VANILLA -> null;
            };
        } catch (Exception e) { return null; }
    }

    private String readEntry(ZipFile z, String name, java.util.function.Function<String, String> parser) {
        var entry = z.getEntry(name); if (entry == null) return null;
        try (var is = z.getInputStream(entry)) { return parser.apply(new String(is.readAllBytes())); } catch (IOException e) { return null; }
    }

    private String parseTomlModId(String toml) {
        for (String l : toml.split("\n")) if (l.trim().startsWith("modId")) { int eq = l.indexOf('='); if (eq > 0) return l.substring(eq + 1).trim().replace("\"", ""); }
        return null;
    }
}
