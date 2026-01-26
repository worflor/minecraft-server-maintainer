package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class Updater {
    private static final String VERSION_PATTERN = "\\d+\\.\\d+(\\.\\d+)?";
    private static final String[] STARTUP_SUCCESS_MARKERS = {"Done (", "For help, type", "Applying patches", "You need to agree to the EULA"};
    private final Path dir;
    private final Config cfg;
    private final Console con;
    private final boolean dry;
    private final Loader loader;
    private final boolean interactive;

    public Updater(Path dir, Config cfg, Console con, boolean dry, Loader loader, boolean interactive) { this.dir = dir; this.cfg = cfg; this.con = con; this.dry = dry; this.loader = loader; this.interactive = interactive; }

    private boolean confirm(String msg) {
        if (!interactive) return true;
        con.showCursor();
        System.out.print("\n  " + msg + " [Y/n]: ");
        System.out.flush();
        try {
            var reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            con.hideCursor();
            if (line == null) return true;
            line = line.trim().toLowerCase();
            return line.isEmpty() || line.equals("y") || line.equals("yes");
        } catch (IOException e) { con.hideCursor(); return true; }
    }

    public int run() {
        if (!checkJava()) { con.fail("Java 21+ required"); return 1; }
        if (!checkDisk()) return 1;
        if (checkPending()) return 1;
        initDirs(); Backup.cleanup(dir, Config.BACKUP_KEEP_DAYS);
        if (!dry) writePending();
        String current = detectVersion();

        if (current == null) {
            con.header(loader.displayName(), "new"); if (dry) con.dryRun();
            con.checking("Fetching latest");
            String target = loader.getLatestSupported(cfg.allowSnapshots);
            if (target == null) { con.fail("Cannot fetch versions"); clearPending(); return 1; }
            con.checkDone(target, true);
            if (!dry) { con.checking("Installing " + loader.displayName()); if (!loader.install(target, dir, con)) { clearPending(); return 1; } con.checkDone("installed", true); writeVersion(target); }
            current = target;
        }

        con.header(loader.displayName(), current); if (dry) con.dryRun();
        if (loader.usesPlugins()) con.warn(loader.displayName() + " devs recommend against auto-updating server JARs");

        boolean checkMods = loader.usesMods() && cfg.updateMods, checkPlugins = loader.usesPlugins() && cfg.updatePlugins, checkDatapacks = cfg.updateDatapacks;
        Path modsDir = dir.resolve("mods"), pluginsDir = dir.resolve("plugins"), datapacksDir = dir.resolve(ModScanner.worldName(dir)).resolve("datapacks");
        boolean hasMods = checkMods && Files.exists(modsDir), hasPlugins = checkPlugins && Files.exists(pluginsDir), hasDatapacks = checkDatapacks && Files.exists(datapacksDir);

        List<String> labels = new ArrayList<>(); labels.add("Minecraft");
        if (checkMods) labels.add("Mods"); if (checkPlugins) labels.add("Plugins"); if (checkDatapacks) labels.add("Datapacks");
        con.setupRows(labels.toArray(new String[0]));
        con.hideCursor();

        String latest;
        if (cfg.targetVersion != null) {
            if (!cfg.targetVersion.matches(VERSION_PATTERN)) {
                con.warn("Invalid target-version format: " + cfg.targetVersion);
                latest = current;
            } else {
                latest = cfg.targetVersion;
            }
        } else {
            latest = Api.getLatestMinecraft(cfg.allowSnapshots);
            if (latest == null) { con.warn("Cannot reach Mojang API"); latest = current; }
        }

        String target = current; boolean mcUp = false; Path backup = null; int row = 0;

        if (current.equals(latest) || !cfg.updateMinecraft) con.rowDone(row++, current);
        else if (!loader.isReady(latest)) con.rowDone(row++, current + " (" + latest + " pending)");
        else {
            var mods = safe(() -> ModScanner.scan(dir.resolve("mods"), loader), List.<ModScanner.Mod>of());
            int compat = mods.isEmpty() ? 100 : checkCompat(mods, latest, row);
            if (compat < cfg.minCompatibility) { con.rowDone(row++, current); con.warn("Update to " + latest + " blocked (" + compat + "% compat)"); }
            else {
                if (!confirm("Update " + loader.displayName() + " " + current + " -> " + latest + "?")) { con.rowDone(row++, current); }
                else {
                    con.rowStatus(row, "backing up");
                    if (!dry) backup = Backup.createSilent(dir, loader, "mc");
                    if (backup == null && !dry) { con.endRows(); con.fail("Backup failed"); clearPending(); con.showCursor(); return 1; }
                    con.rowStatus(row, "installing");
                    if (!dry && !loader.install(latest, dir, con)) { con.endRows(); Backup.restore(backup, dir, loader, con); clearPending(); con.showCursor(); return 1; }
                    if (!dry) { writeVersion(latest); cleanOld(latest); logUpdate(loader.displayName(), current, latest); }
                    con.rowDoneUpdate(row++, current, latest); target = latest; mcUp = true;
                }
            }
        }

        final String mcT = target; int upd = 0;
        if (checkMods) { try { upd += hasMods ? updateContent(row, modsDir, "mod", () -> safe(() -> ModScanner.scan(modsDir, loader), List.of()), m -> Api.checkMod(m, mcT, loader, cfg.allowBeta)) : skip(row); } catch (Exception e) { con.rowSkip(row, "API error"); } row++; }
        if (checkPlugins) { try { upd += hasPlugins ? updateContent(row, pluginsDir, "plugin", () -> safe(() -> ModScanner.scanPlugins(pluginsDir), List.of()), m -> Api.checkMod(m, mcT, Api.PLUGIN_LOADERS, cfg.allowBeta)) : skip(row); } catch (Exception e) { con.rowSkip(row, "API error"); } row++; }
        if (checkDatapacks) { try { upd += hasDatapacks ? updateContent(row, datapacksDir, "datapack", () -> safe(() -> ModScanner.scanDatapacks(datapacksDir), List.of()), m -> Api.checkMod(m, mcT, Api.DATAPACK_LOADERS, cfg.allowBeta)) : skip(row); } catch (Exception e) { con.rowSkip(row, "API error"); } row++; }

        con.endRows(); // done updating rows, safe to print status below
        if (!dry && (mcUp || upd > 0)) { con.blankLine(); checkIntegrity(); if (!verify()) { if (backup != null) Backup.restore(backup, dir, loader, con); clearPending(); con.showCursor(); return 1; } }
        if (!dry) clearPending();
        if (dry) con.showCursor(); else con.countdown();
        return 0;
    }

    private int skip(int row) { con.rowSkip(row, "no folder"); return 0; }
    private <T> T safe(Callable<T> c, T def) { try { return c.call(); } catch (Exception ignored) { return def; } }
    private static boolean containsAny(String s, String[] markers) { for (String m : markers) if (s.contains(m)) return true; return false; }

    private int updateContent(int row, Path d, String type, Supplier<List<ModScanner.Mod>> scanner, Function<ModScanner.Mod, Api.CheckResult> checker) {
        var items = scanner.get();
        if (items.isEmpty()) { con.rowDone(row, "none found"); return 0; }
        var results = runParallel(items, checker, row);
        int ups = 0, cur = 0, skip = 0;
        List<Api.CheckResult> toUp = new ArrayList<>();
        for (var r : results) { if (r == null) skip++; else switch (r.status()) { case "update" -> { ups++; toUp.add(r); } case "current" -> cur++; case "skip" -> skip++; } }
        if (toUp.isEmpty()) { con.rowDone(row, String.valueOf(cur + ups + skip)); return 0; }
        if (dry) { con.rowDone(row, ups + " to update"); for (var r : toUp) con.detail(r.id(), r.oldVersion(), r.newVersion()); return ups; }
        if (!confirm("Update " + ups + " " + type + (ups > 1 ? "s" : "") + "?")) { con.rowDone(row, String.valueOf(cur + skip) + " (skipped " + ups + ")"); return 0; }
        var dls = runParallel(toUp, u -> Http.downloadVerified(u.downloadUrl(), d.resolve(u.fileName()), u.sha512(), 3) ? u : null, row);
        int ok = 0; for (var r : dls) if (r != null) { try { if (!r.path().getFileName().toString().equals(r.fileName())) Files.deleteIfExists(r.path()); } catch (IOException ignored) {} ok++; }
        con.rowDone(row, ok + " updated");
        for (var r : dls) if (r != null) { logUpdate(r.id(), r.oldVersion(), r.newVersion()); con.detail(r.id(), r.oldVersion(), r.newVersion()); }
        return ok;
    }

    private <T, R> List<R> runParallel(List<T> items, Function<T, R> fn, int row) {
        List<R> res = new ArrayList<>();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            var fs = items.stream().map(i -> ex.submit(() -> fn.apply(i))).toList();
            int done = 0; for (var f : fs) { try { res.add(f.get()); } catch (Exception e) { res.add(null); } con.rowProgress(row, ++done, fs.size()); }
        }
        return res;
    }

    private int checkCompat(List<ModScanner.Mod> mods, String mc, int row) {
        var res = runParallel(mods, m -> Api.checkMod(m, mc, loader, cfg.allowBeta), row);
        int compat = 0, total = 0;
        for (var r : res) if (r != null && !r.status().equals("skip")) { total++; if (r.status().equals("current") || r.status().equals("update")) compat++; }
        return total > 0 ? (compat * 100 / total) : 0;
    }

    private boolean verify() {
        con.checking("Verifying");
        String jarName = cfg.serverJar != null ? cfg.serverJar : loader.findServerJar(dir);
        Path jar = dir.resolve(jarName);
        if (!Files.exists(jar)) { con.fail("Server JAR missing"); return false; }
        Process p = null;
        try {
            ProcessBuilder pb;
            if (jarName.endsWith(".bat")) pb = new ProcessBuilder("cmd", "/c", jar.toString());
            else if (jarName.endsWith(".sh")) pb = new ProcessBuilder("bash", jar.toString());
            else pb = new ProcessBuilder("java", "-Xmx1G", "-jar", jar.toString(), "nogui");
            p = pb.directory(dir.toFile()).redirectErrorStream(true).start();
            var is = p.getInputStream();
            var found = new java.util.concurrent.atomic.AtomicBoolean(false);
            Thread.startVirtualThread(() -> {
                try (var rd = new BufferedReader(new InputStreamReader(is))) {
                    String line; while ((line = rd.readLine()) != null) if (containsAny(line, STARTUP_SUCCESS_MARKERS)) found.set(true);
                } catch (IOException ignored) {}
            });
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < cfg.startupTimeout * 1000L) {
                if (found.get() || !p.isAlive()) break;
                Thread.sleep(100);
            }
            if (p.isAlive()) p.destroyForcibly();
            p.waitFor(5, TimeUnit.SECONDS);
            if (found.get()) con.checkDone("ok", true); else con.fail("Server failed to start");
            return found.get();
        } catch (Exception e) { con.fail("Startup error: " + e.getMessage()); return false; }
        finally { if (p != null && p.isAlive()) p.destroyForcibly(); }
    }

    private boolean checkJava() {
        Process p = null;
        try {
            p = new ProcessBuilder("java", "-version").redirectErrorStream(true).start();
            String out; try (var is = p.getInputStream()) { out = new String(is.readAllBytes()); }
            p.waitFor(5, TimeUnit.SECONDS);
            var m = java.util.regex.Pattern.compile("version \"(\\d+)").matcher(out);
            if (m.find()) return Integer.parseInt(m.group(1)) >= 21;
            return false;
        } catch (Exception e) { return false; }
        finally { if (p != null) p.destroyForcibly(); }
    }
    private boolean checkDisk() { try { long mb = Files.getFileStore(dir).getUsableSpace() / (1024 * 1024); if (mb < 500) { con.fail("Need 500MB free (" + mb + "MB available)"); return false; } if (mb < 1000) con.warn("Low disk: " + mb + "MB"); return true; } catch (IOException e) { return true; } }
    private void initDirs() {
        try {
            if (loader.usesMods()) Files.createDirectories(dir.resolve("mods"));
            else if (loader.usesPlugins()) Files.createDirectories(dir.resolve("plugins"));
        } catch (IOException ignored) {}
    }

    private String detectVersion() {
        Path vf = dir.resolve("current_version.txt");
        if (Files.exists(vf)) try { String v = Files.readString(vf).trim(); if (v.matches(VERSION_PATTERN)) return v; } catch (IOException ignored) {}
        Path vd = dir.resolve("versions");
        if (Files.exists(vd)) try (var s = Files.list(vd)) { var f = s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).filter(n -> n.matches(VERSION_PATTERN)).max(Updater::compareVersions); if (f.isPresent()) { writeVersion(f.get()); return f.get(); } } catch (IOException ignored) {}
        return null;
    }

    // Numeric version comparison: 1.20.10 > 1.20.2
    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\."), pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (va != vb) return va - vb;
        }
        return 0;
    }

    private void writeVersion(String v) {
        Path vf = dir.resolve("current_version.txt"), tmp = dir.resolve("current_version.txt.tmp");
        try { Files.writeString(tmp, v); Files.move(tmp, vf, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { try { Files.deleteIfExists(tmp); } catch (IOException e2) {} con.warn("Failed to save version"); }
    }
    private void logUpdate(String name, String oldVer, String newVer) {
        try {
            Path log = dir.resolve("woflo").resolve("update.txt");
            String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm"));
            String old = oldVer != null ? oldVer : "?";
            String entry = "  " + name + "  " + old + " → " + newVer + "  (" + ts + ")";

            List<String> lines = new ArrayList<>();
            lines.add("─── recent updates ───");
            lines.add("");
            lines.add(entry);
            if (Files.exists(log)) {
                var existing = Files.readAllLines(log).stream()
                    .filter(l -> !l.isBlank() && !l.startsWith("───"))
                    .limit(6).toList();
                lines.addAll(existing);
            }
            lines.add("");
            Files.writeString(log, String.join("\n", lines) + "\n");
        } catch (IOException ignored) {}
    }

    private void cleanOld(String keep) {
        Path d = dir.resolve("versions"); if (!Files.exists(d)) return;
        try (var s = Files.list(d)) {
            for (Path p : s.filter(Files::isDirectory).filter(x -> !x.getFileName().toString().equals(keep)).toList()) {
                try { Backup.del(p); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private void checkIntegrity() {
        checkJars(dir.resolve("mods"));
        checkJars(dir.resolve("plugins"));
    }

    private void checkJars(Path d) {
        if (!Files.exists(d)) return;
        try (var s = Files.list(d)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".jar")).toList()) {
                if (Files.size(p) == 0) con.warn("Empty file: " + p.getFileName());
            }
        } catch (IOException ignored) {}
    }

    // pending operation marker for crash recovery
    private Path pendingFile() { return dir.resolve("woflo").resolve(".pending"); }
    private void writePending() { try { Files.writeString(pendingFile(), "update"); } catch (IOException ignored) {} }
    private void clearPending() { try { Files.deleteIfExists(pendingFile()); } catch (IOException ignored) {} }
    private boolean checkPending() {
        Path p = pendingFile();
        if (!Files.exists(p)) return false;
        con.warn("Previous update interrupted - rolling back");
        Path backups = dir.resolve("woflo").resolve("backups");
        if (Files.exists(backups)) {
            try (var s = Files.list(backups)) {
                var latest = s.filter(Files::isDirectory).max(Comparator.comparing(x -> x.getFileName().toString()));
                if (latest.isPresent() && Backup.restore(latest.get(), dir, loader, con)) { clearPending(); return false; }
            } catch (IOException ignored) {}
        }
        con.fail("Cannot recover - manual intervention required");
        return true;
    }
}
