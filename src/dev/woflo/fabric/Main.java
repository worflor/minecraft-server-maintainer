package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    private static Path serverDir;
    private static Console console;
    private static Config config;
    private static Loader loader;

    public static void main(String[] args) {
        if (System.console() == null && !Arrays.asList(args).contains("--no-relaunch") && relaunch()) return;
        boolean dryRun = false, updateOnly = false, rollback = false;
        Boolean interactiveFlag = null; // null = use config, true = force interactive, false = force automated
        for (String a : args) { switch (a) { case "--dry-run", "-d" -> dryRun = true; case "--update-only", "-u" -> updateOnly = true; case "--rollback", "-r" -> rollback = true; case "--interactive", "-i" -> interactiveFlag = true; case "--yes", "-y" -> interactiveFlag = false; case "--help", "-h" -> { printHelp(); return; } } }

        try { serverDir = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent(); } catch (Exception e) {}
        if (serverDir == null) serverDir = Path.of(".").toAbsolutePath();
        if (serverDir.getFileName().toString().equals("woflo")) serverDir = serverDir.getParent(); // support running from woflo/ folder

        try { var wofloDir = serverDir.resolve("woflo"); java.nio.file.Files.createDirectories(wofloDir); console = new Console(wofloDir.resolve("update.log").toFile()); }
        catch (IOException e) { System.err.println("Failed to initialize: " + e.getMessage()); System.exit(1); }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> { console.showCursor(); console.close(); }));

        try {
            config = Config.load(serverDir);
            loader = Loader.detect(serverDir);
            if (rollback) { doRollback(); return; }
            boolean interactive = interactiveFlag != null ? interactiveFlag : config.interactive;
            int exit = new Updater(serverDir, config, console, dryRun, loader, interactive).run();
            if (exit != 0) System.exit(exit);
            if (!dryRun && !updateOnly) runServer();
        } catch (Exception e) { console.showCursor(); console.fail("Fatal: " + e.getMessage()); System.exit(1); }
    }

    private static void doRollback() {
        Path backups = serverDir.resolve("woflo").resolve("backups");
        if (!Files.exists(backups)) { console.fail("No backups folder found"); System.exit(1); }
        try (var s = Files.list(backups)) {
            var latest = s.filter(Files::isDirectory).max(Comparator.comparing(p -> p.getFileName().toString()));
            if (latest.isEmpty()) { console.fail("No backups available"); System.exit(1); }
            console.info("Rolling back to: " + latest.get().getFileName());
            if (Backup.restore(latest.get(), serverDir, loader, console)) {
                console.info("Rollback complete. Restart server to apply.");
            } else { System.exit(1); }
        } catch (IOException e) { console.fail("Rollback failed: " + e.getMessage()); System.exit(1); }
    }

    private static void runServer() {
        String jarName = config.serverJar != null ? config.serverJar : loader.findServerJar(serverDir);
        Path jar = serverDir.resolve(jarName);
        if (!Files.exists(jar)) { console.fail("Server JAR not found: " + jarName); System.exit(1); }
        checkEula();
        var cmd = new ArrayList<String>();
        if (jarName.endsWith(".bat") || jarName.endsWith(".sh")) {
            // Modern Forge/NeoForge use run scripts
            if (jarName.endsWith(".bat")) cmd.addAll(List.of("cmd", "/c", jar.toString()));
            else cmd.addAll(List.of("bash", jar.toString()));
        } else {
            cmd.addAll(List.of("java", "-Xms" + config.memoryMin, "-Xmx" + config.memoryMax));
            cmd.addAll(config.jvmArgs); cmd.addAll(List.of("-jar", jar.toString(), "nogui"));
        }
        long[] crashes = new long[config.maxCrashes]; int ci = 0;
        long windowMs = config.crashWindow * 1000L, delayMs = config.restartDelay * 1000L;
        while (true) {
            try {
                long start = System.currentTimeMillis();
                int exit = new ProcessBuilder(cmd).directory(serverDir.toFile()).inheritIO().start().waitFor();
                long run = System.currentTimeMillis() - start;
                if (exit == 0 || run > windowMs) { crashes = new long[config.maxCrashes]; System.out.println("\nServer stopped. Restarting in " + config.restartDelay + " seconds...\n"); Thread.sleep(delayMs); continue; }
                crashes[ci] = System.currentTimeMillis(); ci = (ci + 1) % config.maxCrashes;
                int recent = 0; long now = System.currentTimeMillis(); for (long t : crashes) if (t > 0 && now - t < windowMs) recent++;
                if (recent >= config.maxCrashes) { console.fail("Server crashed " + config.maxCrashes + " times in " + (config.crashWindow / 60) + " minutes"); console.warn("Check logs. Waiting " + (config.crashWindow / 60) + " minutes..."); crashes = new long[config.maxCrashes]; Thread.sleep(windowMs); }
                else { System.out.println("\nServer crashed (exit " + exit + "). Restarting in " + config.restartDelay + " seconds...\n"); Thread.sleep(delayMs); }
            } catch (InterruptedException e) { break; } catch (Exception e) { System.err.println("Failed to start: " + e.getMessage()); try { Thread.sleep(delayMs); } catch (InterruptedException e2) {} }
        }
    }

    private static void checkEula() {
        Path eula = serverDir.resolve("eula.txt");
        try {
            if (Files.exists(eula) && Files.readString(eula).contains("eula=true")) return;
            console.warn("EULA not accepted. Please read: https://aka.ms/MinecraftEULA");
            console.info("Edit eula.txt and set eula=true to accept, then restart.");
            if (!Files.exists(eula)) Files.writeString(eula, """
                #By changing the setting below to TRUE you are indicating your agreement to our EULA
                #https://aka.ms/MinecraftEULA
                eula=false
                """);
            System.exit(0);
        } catch (IOException e) { console.fail("Cannot check EULA: " + e.getMessage()); System.exit(1); }
    }

    private static boolean relaunch() {
        try {
            String jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", "java", "-jar", jar, "--no-relaunch");
            else if (os.contains("mac")) pb = new ProcessBuilder("open", "-a", "Terminal", Path.of(jar).getParent().toString());
            else { for (String t : new String[]{"gnome-terminal", "konsole", "xfce4-terminal", "xterm"}) { try { if (new ProcessBuilder("which", t).start().waitFor() == 0) { pb = new ProcessBuilder(t, "-e", "java", "-jar", jar, "--no-relaunch"); pb.start(); return true; } } catch (Exception e) {} } return false; }
            pb.start(); return true;
        } catch (Exception e) { return false; }
    }

    private static void printHelp() {
        System.out.println("""

            Server Maintainer                                    woflo

            Usage: java -jar "server maintainer by woflo.jar" [options]

            Options:
              -d, --dry-run      Preview changes without applying
              -u, --update-only  Update only, don't start server
              -r, --rollback     Restore most recent backup
              -i, --interactive  Prompt before updates
              -y, --yes          Skip prompts (overrides config)
              -h, --help         Show this help

            Files: woflo/woflo.yml (config), mods/mods.txt (skip mods)
            """);
    }
}
