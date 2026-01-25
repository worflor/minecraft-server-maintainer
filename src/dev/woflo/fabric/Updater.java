package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class Updater {
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
        initDirs(); Backup.cleanup(dir, Config.BACKUP_KEEP_DAYS);
        String current = detectVersion();

        if (current == null) {
            con.header(loader.displayName(), "new"); if (dry) con.dryRun();
            con.checking("Fetching latest");
            String target = loader.getLatestSupported(cfg.allowSnapshots);
            if (target == null) { con.fail("Cannot fetch versions"); return 1; }
            con.checkDone(target, true);
            if (!dry) { con.checking("Installing " + loader.displayName()); if (!loader.install(target, dir, con)) return 1; con.checkDone("installed", true); writeVersion(target); }
            current = target;
        }

        con.header(loader.displayName(), current); if (dry) con.dryRun();

        boolean checkMods = loader != Loader.VANILLA && cfg.updateMods, checkPlugins = cfg.updatePlugins, checkDatapacks = cfg.updateDatapacks;
        Path modsDir = dir.resolve("mods"), pluginsDir = dir.resolve("plugins"), datapacksDir = dir.resolve(getWorldName()).resolve("datapacks");
        boolean hasMods = checkMods && Files.exists(modsDir), hasPlugins = checkPlugins && Files.exists(pluginsDir), hasDatapacks = checkDatapacks && Files.exists(datapacksDir);

        List<String> labels = new ArrayList<>(); labels.add("Minecraft");
        if (checkMods) labels.add("Mods"); if (checkPlugins) labels.add("Plugins"); if (checkDatapacks) labels.add("Datapacks");
        con.setupRows(labels.toArray(new String[0]));
        con.hideCursor();

        String latest = cfg.targetVersion != null ? cfg.targetVersion : Api.getLatestMinecraft(cfg.allowSnapshots);
        if (latest == null) { con.warn("Cannot reach Mojang API"); latest = current; }

        String target = current; boolean mcUp = false; Path backup = null; int row = 0;

        if (current.equals(latest) || !cfg.updateMinecraft) con.rowDone(row++, current);
        else if (!loader.isReady(latest)) con.rowDone(row++, current + " (" + latest + " pending)");
        else {
            List<ModScanner.Mod> mods; try { mods = ModScanner.scan(dir.resolve("mods"), loader); } catch (IOException e) { mods = new ArrayList<>(); }
            int compat = mods.isEmpty() ? 100 : checkCompat(mods, latest, row);
            if (compat < cfg.minCompatibility) { con.rowDone(row++, current); con.warn("Update to " + latest + " blocked (" + compat + "% compat)"); }
            else if (!confirm("Update Minecraft " + current + " -> " + latest + "?")) { con.rowDone(row++, current); }
            else {
                backup = Backup.create(dir, loader, "mc", con);
                if (backup == null && !dry) { con.fail("Backup required"); con.showCursor(); return 1; }
                if (!dry && !loader.install(latest, dir, con)) { Backup.restore(backup, dir, loader, con); con.showCursor(); return 1; }
                if (!dry) { writeVersion(latest); cleanOld(latest); }
                con.rowDoneUpdate(row++, current, latest); target = latest; mcUp = true;
            }
        }

        final String mcT = target; int upd = 0;
        if (checkMods) { upd += hasMods ? updateContent(row, modsDir, "mod", () -> scan(modsDir, loader), m -> Api.checkMod(m, mcT, loader, cfg.allowBeta)) : skip(row); row++; }
        if (checkPlugins) { upd += hasPlugins ? updateContent(row, pluginsDir, "plugin", () -> scanP(pluginsDir), m -> Api.checkMod(m, mcT, Api.PLUGIN_LOADERS, cfg.allowBeta)) : skip(row); row++; }
        if (checkDatapacks) { upd += hasDatapacks ? updateContent(row, datapacksDir, "datapack", () -> scanD(datapacksDir), m -> Api.checkMod(m, mcT, Api.DATAPACK_LOADERS, cfg.allowBeta)) : skip(row); row++; }

        if (!dry && (mcUp || upd > 0)) { con.blankLine(); if (!verify()) { if (backup != null) Backup.restore(backup, dir, loader, con); con.showCursor(); return 1; } }
        if (dry) con.showCursor(); else con.countdown();
        return 0;
    }

    private int skip(int row) { con.rowSkip(row, "no folder"); return 0; }
    private List<ModScanner.Mod> scan(Path d, Loader l) { try { return ModScanner.scan(d, l); } catch (IOException e) { return List.of(); } }
    private List<ModScanner.Mod> scanP(Path d) { try { return ModScanner.scanPlugins(d); } catch (IOException e) { return List.of(); } }
    private List<ModScanner.Mod> scanD(Path d) { try { return ModScanner.scanDatapacks(d); } catch (IOException e) { return List.of(); } }

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
        int ok = 0; for (var r : dls) if (r != null) { try { if (!r.path().getFileName().toString().equals(r.fileName())) Files.deleteIfExists(r.path()); } catch (IOException e) {} ok++; }
        con.rowDone(row, ok + " updated");
        for (var r : dls) if (r != null) con.detail(r.id(), r.oldVersion(), r.newVersion());
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
        return total > 0 ? (compat * 100 / total) : 100;
    }

    private boolean verify() {
        con.checking("Verifying");
        String jarName = cfg.serverJar != null ? cfg.serverJar : loader.findServerJar(dir);
        Path jar = dir.resolve(jarName);
        if (!Files.exists(jar)) { con.fail("Server JAR missing"); return false; }
        try {
            ProcessBuilder pb;
            if (jarName.endsWith(".bat")) pb = new ProcessBuilder("cmd", "/c", jar.toString());
            else if (jarName.endsWith(".sh")) pb = new ProcessBuilder("bash", jar.toString());
            else pb = new ProcessBuilder("java", "-Xmx1G", "-jar", jar.toString(), "nogui");
            var p = pb.directory(dir.toFile()).redirectErrorStream(true).start();
            var rd = new BufferedReader(new InputStreamReader(p.getInputStream()));
            long start = System.currentTimeMillis(); boolean ok = false;
            while (System.currentTimeMillis() - start < cfg.startupTimeout * 1000L) {
                if (rd.ready()) { String l = rd.readLine(); if (l != null && (l.contains("Done (") || l.contains("For help, type"))) { ok = true; break; } }
                if (!p.isAlive()) break; Thread.sleep(100);
            }
            if (p.isAlive()) { p.destroyForcibly(); p.waitFor(5, TimeUnit.SECONDS); }
            if (ok) con.checkDone("ok", true); else con.fail("Server failed to start");
            return ok;
        } catch (Exception e) { con.fail("Startup error: " + e.getMessage()); return false; }
    }

    private boolean checkJava() {
        try {
            var p = new ProcessBuilder("java", "-version").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            var m = java.util.regex.Pattern.compile("version \"(\\d+)").matcher(out);
            if (m.find()) return Integer.parseInt(m.group(1)) >= 21;
            return false;
        } catch (Exception e) { return false; }
    }
    private boolean checkDisk() { try { long mb = Files.getFileStore(dir).getUsableSpace() / (1024 * 1024); if (mb < 500) { con.fail("Need 500MB free (" + mb + "MB available)"); return false; } if (mb < 1000) con.warn("Low disk: " + mb + "MB"); return true; } catch (IOException e) { return true; } }
    private void initDirs() { try { Files.createDirectories(dir.resolve("mods")); } catch (IOException e) {} }

    private String detectVersion() {
        Path vf = dir.resolve("current_version.txt");
        if (Files.exists(vf)) try { String v = Files.readString(vf).trim(); if (v.matches("\\d+\\.\\d+(\\.\\d+)?")) return v; } catch (IOException e) {}
        Path vd = dir.resolve("versions");
        if (Files.exists(vd)) try (var s = Files.list(vd)) { var f = s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).filter(n -> n.matches("\\d+\\.\\d+(\\.\\d+)?")).max(Comparator.naturalOrder()); if (f.isPresent()) { writeVersion(f.get()); return f.get(); } } catch (IOException e) {}
        return null;
    }

    private void writeVersion(String v) { try { Files.writeString(dir.resolve("current_version.txt"), v); } catch (IOException e) {} }
    private void cleanOld(String keep) {
        Path d = dir.resolve("versions"); if (!Files.exists(d)) return;
        try (var s = Files.list(d)) {
            for (Path p : s.filter(Files::isDirectory).filter(x -> !x.getFileName().toString().equals(keep)).toList()) {
                try { Backup.del(p); } catch (IOException e) {}
            }
        } catch (IOException e) {}
    }
    private String getWorldName() { Path p = dir.resolve("server.properties"); if (Files.exists(p)) try { for (String l : Files.readAllLines(p)) if (l.startsWith("level-name=")) return l.substring(11).trim(); } catch (IOException e) {} return "world"; }
}
