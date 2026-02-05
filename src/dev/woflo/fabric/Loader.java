package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public enum Loader {
    FABRIC, FORGE, NEOFORGE, QUILT, PAPER, PURPUR, FOLIA, VANILLA;

    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    private static final String QUILT_META = "https://meta.quiltmc.org/v3";
    private static final String FORGE_META = "https://files.minecraftforge.net";
    private static final String NEOFORGE_META = "https://maven.neoforged.net";
    private static final String MOJANG_META = "https://launchermeta.mojang.com";
    private static final String PAPER_API = "https://api.papermc.io/v2/projects";
    private static final String PURPUR_API = "https://api.purpurmc.org/v2/purpur";
    private static final String V = "current_version.txt";

    public static Loader detect(Path dir) {
        if (Files.exists(dir.resolve("fabric-server-launch.jar")) || Files.exists(dir.resolve(".fabric"))) return FABRIC;
        if (Files.exists(dir.resolve("quilt-server-launch.jar")) || Files.exists(dir.resolve(".quilt"))) return QUILT;

        Path mods = dir.resolve("mods");
        if (Files.exists(mods)) {
            try (var s = Files.list(mods)) {
                for (Path p : s.filter(f -> f.toString().endsWith(".jar")).toList()) {
                    String d = detectFromMod(p);
                    Loader l = switch (d) { case "fabric" -> FABRIC; case "forge" -> FORGE; case "neoforge" -> NEOFORGE; case "quilt" -> QUILT; default -> null; };
                    if (l != null) return l;
                }
            } catch (IOException ignored) {}
        }

        try (var s = Files.list(dir)) {
            if (s.anyMatch(p -> p.getFileName().toString().toLowerCase().contains("forge") && p.toString().endsWith(".jar"))) return FORGE;
        } catch (IOException ignored) {}

        try (var s = Files.list(dir)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".jar")).toList()) {
                Loader l = detectServerJar(p);
                if (l != null) return l;
            }
        } catch (IOException ignored) {}

        if (Files.exists(dir.resolve("plugins")) && !Files.exists(mods)) return PAPER;

        if (Files.exists(dir.resolve("server.jar")) && (!Files.exists(mods) || isEmpty(mods))) return VANILLA;
        return FABRIC;
    }

    private static Loader detectServerJar(Path jar) {
        try (var z = new ZipFile(jar.toFile())) {
            var manifest = z.getEntry("META-INF/MANIFEST.MF");
            if (manifest != null) {
                String mf = new String(z.getInputStream(manifest).readAllBytes());
                if (mf.contains("io.papermc.paperclip.Main")) {
                    String name = jar.getFileName().toString().toLowerCase();
                    if (name.contains("folia")) return FOLIA;
                    if (name.contains("purpur")) return PURPUR;
                    return PAPER;
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static boolean isEmpty(Path d) { try (var s = Files.list(d)) { return s.findFirst().isEmpty(); } catch (IOException e) { return true; } }

    private static String detectFromMod(Path jar) {
        try (var z = new ZipFile(jar.toFile())) {
            if (z.getEntry("fabric.mod.json") != null) return "fabric";
            if (z.getEntry("quilt.mod.json") != null) return "quilt";
            // NeoForge before Forge - some mods have both for backwards compat
            if (z.getEntry("META-INF/neoforge.mods.toml") != null) return "neoforge";
            if (z.getEntry("META-INF/mods.toml") != null) return "forge";
        } catch (IOException ignored) {}
        return null;
    }

    public boolean usesPlugins() { return this == PAPER || this == PURPUR || this == FOLIA; }
    public boolean usesMods() { return this == FABRIC || this == QUILT || this == FORGE || this == NEOFORGE; }

    public String serverJar() {
        return switch (this) {
            case FABRIC -> "fabric-server-launch.jar";
            case QUILT -> "quilt-server-launch.jar";
            case FORGE -> "forge-server.jar";
            case NEOFORGE -> "neoforge-server.jar";
            case PAPER -> "paper.jar";
            case PURPUR -> "purpur.jar";
            case FOLIA -> "folia.jar";
            case VANILLA -> "server.jar";
        };
    }

    public String findServerJar(Path dir) {
        String expected = serverJar();
        if (Files.exists(dir.resolve(expected))) return expected;

        // Modern Forge/NeoForge use run scripts - check these FIRST
        if (this == FORGE || this == NEOFORGE) {
            boolean hasRunBat = Files.exists(dir.resolve("run.bat"));
            boolean hasRunSh = Files.exists(dir.resolve("run.sh"));
            if (hasRunBat || hasRunSh) {
                return System.getProperty("os.name").toLowerCase().contains("win") && hasRunBat ? "run.bat" : "run.sh";
            }
        }

        if (usesPlugins()) {
            try (var s = Files.list(dir)) {
                for (Path p : s.filter(f -> f.toString().endsWith(".jar")).toList()) {
                    if (detectServerJar(p) == this) return p.getFileName().toString();
                }
            } catch (IOException ignored) {}
        }

        String search = switch (this) {
            case FABRIC -> "fabric"; case QUILT -> "quilt"; case FORGE -> "forge"; case NEOFORGE -> "neoforge";
            case PAPER -> "paper"; case PURPUR -> "purpur"; case FOLIA -> "folia"; case VANILLA -> "server";
        };

        try (var s = Files.list(dir)) {
            var jars = s.filter(p -> p.toString().endsWith(".jar"))
                .map(p -> p.getFileName().toString())
                .filter(n -> n.toLowerCase().contains(search))
                .filter(n -> !n.toLowerCase().contains("installer"))
                .filter(n -> !n.toLowerCase().contains("woflo") && !n.toLowerCase().contains("maintainer"))
                .toList();
            if (jars.size() == 1) return jars.get(0);
            if (jars.size() > 1) throw new RuntimeException("Multiple " + displayName() + " JARs: " + String.join(", ", jars) + " - set server-jar in config");
        } catch (IOException ignored) {}

        return expected;
    }

    public String displayName() {
        return switch (this) {
            case FABRIC -> "Fabric"; case FORGE -> "Forge"; case NEOFORGE -> "NeoForge"; case QUILT -> "Quilt";
            case PAPER -> "Paper"; case PURPUR -> "Purpur"; case FOLIA -> "Folia"; case VANILLA -> "Vanilla";
        };
    }

    public List<String> modrinthLoaders() {
        return switch (this) {
            case FABRIC -> List.of("fabric", "quilt");
            case QUILT -> List.of("quilt", "fabric");
            case NEOFORGE -> List.of("neoforge", "forge");
            case FORGE -> List.of("forge", "neoforge");
            case PAPER, PURPUR, FOLIA -> List.of("paper", "spigot", "bukkit");
            case VANILLA -> List.of();
        };
    }

    public Set<String> ignoredModIds() {
        return switch (this) {
            case FABRIC -> Set.of("java", "minecraft", "fabricloader", "mixinextras", "fabric-api");
            case QUILT -> Set.of("java", "minecraft", "quilt_loader", "quilted_fabric_api");
            case FORGE -> Set.of("minecraft", "forge");
            case NEOFORGE -> Set.of("minecraft", "neoforge");
            case PAPER, PURPUR, FOLIA, VANILLA -> Set.of();
        };
    }

    public String[] backupItems() {
        return switch (this) {
            case FABRIC -> new String[]{"mods", "versions", "libraries", "fabric-server-launch.jar", V};
            case QUILT -> new String[]{"mods", "versions", "libraries", "quilt-server-launch.jar", V};
            case FORGE, NEOFORGE -> new String[]{"mods", "libraries", "run.bat", "run.sh", V};
            case PAPER -> new String[]{"plugins", "paper.jar", V};
            case PURPUR -> new String[]{"plugins", "purpur.jar", V};
            case FOLIA -> new String[]{"plugins", "folia.jar", V};
            case VANILLA -> new String[]{"server.jar", V};
        };
    }

    public boolean isReady(String mc) {
        try { return switch (this) {
            case FABRIC -> !Http.getJsonArray(FABRIC_META + "/versions/loader/" + mc).isEmpty();
            case QUILT -> !Http.getJsonArray(QUILT_META + "/versions/loader/" + mc).isEmpty();
            case FORGE -> { var promos = Http.obj(Http.getJson(FORGE_META + "/net/minecraftforge/forge/promotions_slim.json"), "promos"); yield promos != null && promos.containsKey(mc + "-latest"); }
            case NEOFORGE -> { String[] p = mc.split("\\."); String pfx = neoforgePrefix(p); yield Http.list(Http.getJson(NEOFORGE_META + "/api/maven/versions/releases/net/neoforged/neoforge"), "versions").stream().anyMatch(v -> v.toString().startsWith(pfx)); }
            case PAPER, FOLIA -> !Http.arr(Http.getJson(PAPER_API + "/" + name().toLowerCase() + "/versions/" + Http.encode(mc) + "/builds"), "builds").isEmpty();
            case PURPUR -> { Http.getJson(PURPUR_API + "/" + Http.encode(mc)); yield true; }
            case VANILLA -> true;
        }; } catch (Exception ignored) { return false; }
    }

    private static String neoforgePrefix(String[] parts) {
        return (parts.length >= 2 ? parts[1] : "21") + "." + (parts.length >= 3 ? parts[2] : "0") + ".";
    }

    @SuppressWarnings("unchecked")
    public String getLatestSupported(boolean allowSnapshots) {
        try {
            String url = switch (this) {
                case FABRIC -> FABRIC_META + "/versions/game";
                case QUILT -> QUILT_META + "/versions/game";
                case PAPER, FOLIA -> PAPER_API + "/" + name().toLowerCase();
                case PURPUR -> PURPUR_API;
                default -> null;
            };
            if (url != null) {
                if (this == FABRIC || this == QUILT) {
                    for (Object o : Http.getJsonArray(url)) {
                        var g = (Map<String, Object>) o;
                        if (allowSnapshots || Http.bool(g, "stable", false)) return Http.str(g, "version");
                    }
                    return null;
                }
                if (usesPlugins()) {
                    var versions = Http.list(Http.getJson(url), "versions");
                    return versions.isEmpty() ? null : versions.getLast().toString();
                }
            }
            var latest = Http.obj(Http.getJson(MOJANG_META + "/mc/game/version_manifest_v2.json"), "latest");
            return latest != null ? Http.str(latest, allowSnapshots ? "snapshot" : "release") : null;
        } catch (Exception ignored) { return null; }
    }

    public boolean install(String mc, Path dir, Console c) {
        try { return switch (this) {
            case FABRIC -> runInstaller(Http.getJsonArray(FABRIC_META + "/versions/installer"), dir, c, "java", "-jar", "installer.jar", "server", "-mcversion", mc, "-downloadMinecraft");
            case QUILT -> runInstaller(Http.getJsonArray(QUILT_META + "/versions/installer"), dir, c, "java", "-jar", "installer.jar", "install", "server", mc, "--install-dir=.", "--download-server");
            case FORGE -> installForge(mc, dir, c);
            case NEOFORGE -> installNeoForge(mc, dir, c);
            case PAPER, FOLIA -> installPaper(name().toLowerCase(), mc, dir, c);
            case PURPUR -> installPurpur(mc, dir, c);
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
        var p = new ProcessBuilder(full).directory(dir.toFile()).redirectErrorStream(true).start();
        try (var is = p.getInputStream()) { is.transferTo(java.io.OutputStream.nullOutputStream()); }
        int exit = p.waitFor();
        Files.deleteIfExists(installer);
        return exit == 0;
    }

    private boolean installForge(String mc, Path dir, Console c) throws Exception {
        var promos = Http.obj(Http.getJson(FORGE_META + "/net/minecraftforge/forge/promotions_slim.json"), "promos");
        String fv = Http.str(promos, mc + "-recommended");
        if (fv == null) fv = Http.str(promos, mc + "-latest");
        if (fv == null) { c.fail("No Forge for MC " + mc); return false; }
        String full = mc + "-" + fv;
        String url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + full + "/forge-" + full + "-installer.jar";
        return runInstaller(List.of(Map.of("url", url)), dir, c, "java", "-jar", "installer.jar", "--installServer");
    }

    private boolean installNeoForge(String mc, Path dir, Console c) throws Exception {
        String[] p = mc.split("\\.");
        String pfx = neoforgePrefix(p);
        var vers = Http.list(Http.getJson(NEOFORGE_META + "/api/maven/versions/releases/net/neoforged/neoforge"), "versions");
        String nv = null, fallback = null;
        for (var o : vers.reversed()) {
            String v = o.toString();
            if (v.startsWith(pfx)) {
                if (!v.contains("-beta") && !v.contains("-alpha")) { nv = v; break; }
                if (fallback == null) fallback = v;
            }
        }
        if (nv == null) nv = fallback;
        if (nv == null) { c.fail("No NeoForge for MC " + mc); return false; }
        String url = NEOFORGE_META + "/releases/net/neoforged/neoforge/" + nv + "/neoforge-" + nv + "-installer.jar";
        return runInstaller(List.of(Map.of("url", url)), dir, c, "java", "-jar", "installer.jar", "--installServer");
    }

    private boolean installPaper(String project, String mc, Path dir, Console c) throws Exception {
        String encMc = Http.encode(mc);
        var data = Http.getJson(PAPER_API + "/" + project + "/versions/" + encMc + "/builds");
        var builds = Http.arr(data, "builds");
        if (builds.isEmpty()) { c.fail("No " + project + " builds for MC " + mc); return false; }
        var latest = builds.getLast();
        var buildNum = latest.get("build");
        if (buildNum == null) { c.fail("Invalid build data for " + project); return false; }
        int build = ((Number) buildNum).intValue();
        var downloads = Http.obj(latest, "downloads");
        var app = downloads != null ? Http.obj(downloads, "application") : null;
        String filename = app != null ? Http.str(app, "name") : null;
        if (filename == null) { c.fail("No download available for " + project); return false; }
        String url = PAPER_API + "/" + project + "/versions/" + encMc + "/builds/" + build + "/downloads/" + Http.encode(filename);
        Http.download(url, dir.resolve(project + ".jar"));
        return true;
    }

    private boolean installPurpur(String mc, Path dir, Console c) throws Exception {
        String url = PURPUR_API + "/" + Http.encode(mc) + "/latest/download";
        Http.download(url, dir.resolve("purpur.jar"));
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean installVanilla(String mc, Path dir, Console c) throws Exception {
        var manifest = Http.getJson(MOJANG_META + "/mc/game/version_manifest_v2.json");
        String vUrl = null;
        for (var v : Http.list(manifest, "versions")) {
            var m = (Map<String, Object>) v;
            if (mc.equals(Http.str(m, "id"))) { vUrl = Http.str(m, "url"); break; }
        }
        if (vUrl == null) { c.fail("Version " + mc + " not found"); return false; }
        var downloads = Http.obj(Http.getJson(vUrl), "downloads");
        if (downloads == null) { c.fail("No downloads for " + mc); return false; }
        var server = Http.obj(downloads, "server");
        if (server == null) { c.fail("No server JAR for " + mc); return false; }
        String downloadUrl = Http.str(server, "url");
        if (downloadUrl == null) { c.fail("No download URL for " + mc); return false; }
        Http.download(downloadUrl, dir.resolve("server.jar"));
        return true;
    }

    public String readModId(Path jar) {
        if (usesPlugins()) return null; // Plugins handled by ModScanner
        try (var z = new ZipFile(jar.toFile())) {
            return switch (this) {
                case FABRIC -> readEntry(z, "fabric.mod.json", j -> Http.str(Http.parseObject(j), "id"));
                case QUILT -> { String id = readEntry(z, "quilt.mod.json", j -> Http.str(Http.obj(Http.parseObject(j), "quilt_loader"), "id")); yield id != null ? id : readEntry(z, "fabric.mod.json", j -> Http.str(Http.parseObject(j), "id")); }
                case FORGE, NEOFORGE -> readEntry(z, z.getEntry("META-INF/neoforge.mods.toml") != null ? "META-INF/neoforge.mods.toml" : "META-INF/mods.toml", this::parseTomlModId);
                default -> null;
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
