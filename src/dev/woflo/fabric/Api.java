package dev.woflo.fabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class Api {
    private static final String MOJANG_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    public static String getLatestMinecraft(boolean allowSnapshots) {
        try {
            var latest = Http.obj(Http.getJson(MOJANG_MANIFEST), "latest");
            return Http.str(latest, allowSnapshots ? "snapshot" : "release");
        } catch (Exception ignored) { return null; }
    }

    public record CheckResult(String id, Path path, String status, String oldVersion, String newVersion, String downloadUrl, String sha512, String fileName) {}

    public static final List<String> PLUGIN_LOADERS = List.of("paper", "spigot", "bukkit", "folia");
    public static final List<String> DATAPACK_LOADERS = List.of("datapack");

    public static CheckResult checkMod(ModScanner.Mod mod, String mcVersion, Loader loader, boolean allowBeta) { return checkMod(mod, mcVersion, loader.modrinthLoaders(), allowBeta); }

    private static CheckResult skip(ModScanner.Mod m, String v) { return new CheckResult(m.id(), m.path(), "skip", v, null, null, null, null); }

    public static CheckResult checkMod(ModScanner.Mod mod, String mcVersion, List<String> loaders, boolean allowBeta) {
        if (loaders.isEmpty()) return skip(mod, null);
        try {
            String hash = Http.sha512(mod.path());
            Map<String, Object> current;
            try { current = Http.getJson(MODRINTH_API + "/version_file/" + hash); }
            catch (IOException ignored) { return skip(mod, null); }

            String projectId = Http.str(current, "project_id"), oldVer = Http.str(current, "version_number");
            List<Object> versions = null;
            for (String l : loaders) {
                versions = Http.getJsonArray(MODRINTH_API + "/project/" + Http.encode(projectId) +
                    "/version?game_versions=" + Http.encode("[\"" + mcVersion + "\"]") +
                    "&loaders=" + Http.encode("[\"" + l + "\"]"));
                if (versions != null && !versions.isEmpty()) break;
            }
            if (versions == null || versions.isEmpty()) return skip(mod, oldVer);

            // prefer release, allow beta if enabled
            Map<String, Object> latest = null;
            for (var v : versions) {
                @SuppressWarnings("unchecked") var ver = (Map<String, Object>) v;
                String type = Http.str(ver, "version_type");
                if ("release".equals(type) || (allowBeta && "beta".equals(type))) { latest = ver; break; }
            }
            if (latest == null) return skip(mod, oldVer);
            String newVer = Http.str(latest, "version_number");
            var files = Http.arr(latest, "files");
            Map<String, Object> file = null;
            for (var f : files) if (Http.bool(f, "primary", false)) { file = f; break; }
            if (file == null && !files.isEmpty()) file = files.getFirst();
            if (file == null) return skip(mod, oldVer);

            if (oldVer != null && oldVer.equals(newVer)) return new CheckResult(mod.id(), mod.path(), "current", oldVer, newVer, null, null, null);
            var hashes = Http.obj(file, "hashes");
            return new CheckResult(mod.id(), mod.path(), "update", oldVer, newVer, Http.str(file, "url"), hashes != null ? Http.str(hashes, "sha512") : null, Http.str(file, "filename"));
        } catch (Exception ignored) {
            return skip(mod, null);
        }
    }
}
