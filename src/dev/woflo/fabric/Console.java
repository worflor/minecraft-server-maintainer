package dev.woflo.fabric;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Console {
    // ANSI codes
    private static final String R = "\u001B[0m", P = "\u001B[35m", G = "\u001B[32m", Y = "\u001B[33m";
    private static final String E = "\u001B[31m", A = "\u001B[90m", W = "\u001B[97m";
    private static final String SAVE = "\u001B[s", RESTORE = "\u001B[u", CL = "\u001B[2K";
    // Box drawing and symbols (instance - tier-dependent)
    private final String H, V, TL, TR, BL, BR, CHK, BLK, SHD, ARW;
    private static final int HEADER_WIDTH = 48, BAR_WIDTH = 16, COUNTDOWN_WIDTH = 52, COUNTDOWN_FRAMES = 30;
    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrintWriter log;
    private final int tier;
    private final boolean tty;
    private boolean rowsPrinted = false;
    private List<String> labels = new ArrayList<>();
    private String[] rowStatus, rowValue;

    public Console(File logFile) throws IOException {
        log = new PrintWriter(new FileWriter(logFile, true), true);
        tier = detectTier();
        boolean unicode = tier >= 3;
        H = unicode ? "\u2500" : "-";
        V = unicode ? "\u2502" : "|";
        TL = unicode ? "\u250C" : "+";
        TR = unicode ? "\u2510" : "+";
        BL = unicode ? "\u2514" : "+";
        BR = unicode ? "\u2518" : "+";
        CHK = unicode ? "\u221A" : "[OK]";
        BLK = unicode ? "\u2588" : "#";
        SHD = unicode ? "\u2591" : ".";
        ARW = unicode ? "\u2192" : "->";
        tty = tier >= 2;
    }

    private static int detectTier() {
        if (System.getenv("FORCE_COLOR") != null)
            return 4;
        if (System.getenv("NO_COLOR") != null)
            return 1;
        String override = System.getProperty("server.maintainer.term", System.getenv("SERVER_MAINTAINER_TERM"));
        if (override != null)
            return switch (override.toLowerCase()) {
                case "full" -> 4;
                case "standard" -> 3;
                case "basic" -> 2;
                default -> 1;
            };
        if (System.console() == null)
            return 1;
        String term = System.getenv("TERM");
        if (term == null || term.equals("dumb"))
            return 1;
        if (System.getenv("CI") != null)
            return 2;
        boolean utf8 = java.nio.charset.Charset.defaultCharset().name().toUpperCase().contains("UTF");
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            if (System.getenv("WT_SESSION") != null || System.getenv("ConEmuPID") != null)
                return 4;
            String termProg = System.getenv("TERM_PROGRAM");
            if (termProg != null && termProg.toLowerCase().contains("mintty"))
                return 4;
            return utf8 ? 3 : 2;
        }
        if (term.contains("256color"))
            return utf8 ? 4 : 2;
        if (term.contains("screen") || term.contains("tmux"))
            return utf8 ? 3 : 2;
        if (term.contains("xterm"))
            return utf8 ? 3 : 2;
        return utf8 ? 3 : 2;
    }

    // Color helpers
    private String col(String s, String c) { return tty ? c + s + R : s; }
    private String p(String s) { return col(s, P); }
    private String g(String s) { return col(s, G); }
    private String y(String s) { return col(s, Y); }
    private String r(String s) { return col(s, E); }
    private String a(String s) { return col(s, A); }
    private String w(String s) { return col(s, W); }

    private void log(String type, String msg) {
        log.println("[" + LocalDateTime.now().format(LOG_FMT) + "] " + type + " | " + msg);
    }

    public void header(String loader, String ver) {
        String title = "  Server Maintainer", brand = "woflo  ", sub = "  " + loader + " " + ver;
        String hr = H.repeat(HEADER_WIDTH);
        System.out.println("\n  " + p(TL + hr + TR));
        System.out.println("  " + p(V) + w(title) + " ".repeat(HEADER_WIDTH - title.length() - brand.length()) + a(brand) + p(V));
        System.out.println("  " + p(V) + a(sub) + " ".repeat(HEADER_WIDTH - sub.length()) + p(V));
        System.out.println("  " + p(BL + hr + BR) + "\n");
    }

    public void dryRun() { System.out.println("  " + a("(dry run)") + "\n"); }

    public void setupRows(String... lbl) {
        labels = new ArrayList<>(Arrays.asList(lbl));
        rowStatus = new String[lbl.length];
        rowValue = new String[lbl.length];
        Arrays.fill(rowStatus, a(SHD));
        Arrays.fill(rowValue, "");
        rowsPrinted = false;
        if (tier >= 4)
            System.out.print(SAVE);
        redrawRows();
    }

    private void redrawRows() {
        if (tier >= 4)
            System.out.print(RESTORE);
        else if (tier >= 2 && rowsPrinted && !labels.isEmpty())
            System.out.print("\u001B[" + labels.size() + "A");
        rowsPrinted = true;
        for (int i = 0; i < labels.size(); i++) {
            if (tier >= 2)
                System.out.print(CL);
            System.out.println(fmt(labels.get(i), rowStatus[i], rowValue[i]));
        }
        System.out.flush();
    }

    private String fmt(String label, String status, String value) {
        return "  " + w(String.format("%-12s", label)) + " " + status + "  " + value;
    }

    public void rowProgress(int i, int done, int total) {
        if (i >= labels.size())
            return;
        int f = total > 0 ? (done * BAR_WIDTH / total) : 0;
        rowStatus[i] = p(BLK);
        rowValue[i] = a("[") + p(BLK.repeat(f) + SHD.repeat(BAR_WIDTH - f)) + a("] ") + w(done + "/" + total);
        redrawRows();
    }

    public void rowDone(int i, String v) {
        if (i >= labels.size())
            return;
        rowStatus[i] = g(BLK);
        rowValue[i] = w(v) + " " + g(CHK);
        redrawRows();
        log("OK", labels.get(i) + " " + v);
    }

    public void rowDoneUpdate(int i, String from, String to) {
        if (i >= labels.size())
            return;
        rowStatus[i] = g(BLK);
        rowValue[i] = a(from) + " " + a(ARW) + " " + g(to) + " " + g(CHK);
        redrawRows();
        log("Update", labels.get(i) + " " + from + " -> " + to);
    }

    public void rowSkip(int i, String reason) {
        if (i >= labels.size())
            return;
        rowStatus[i] = a(SHD);
        rowValue[i] = a(reason);
        redrawRows();
    }

    public void rowStatus(int i, String status) {
        if (i >= labels.size())
            return;
        rowStatus[i] = y(BLK);
        rowValue[i] = a(status + "...");
        redrawRows();
    }

    public void endRows() {
        rowsPrinted = false;
    }

    public void detail(String name, String from, String to) {
        String n = name.length() <= 18 ? name : name.substring(0, 17) + "~";
        System.out.println("    " + g(CHK) + " " + w(n) + " " + a(from + " " + ARW + " ") + g(to));
        System.out.flush();
        log("Update", name + " " + from + " -> " + to);
    }

    public void blankLine() {
        System.out.println();
    }

    public void info(String msg) { System.out.println("  " + a(msg)); log("Info", msg); }
    public void warn(String msg) { System.out.println("  " + y("! " + msg)); log("WARN", msg); }
    public void fail(String msg) { System.out.println("  " + r("X " + msg)); log("ERROR", msg); }
    public void checking(String what) { System.out.print("  " + a(what + "...")); System.out.flush(); }

    public void checkDone(String result, boolean ok) {
        System.out.println(" " + (ok ? w(result) + " " + g(CHK) : y(result)));
    }

    public void progress(String msg) {
        if (tier < 2) return;
        hideCursor();
        System.out.print("\r" + CL + "  " + a(msg + "..."));
        System.out.flush();
    }

    public void progressDone(String label, String result) {
        if (tier < 2) { System.out.println("  " + label + " " + result); log("OK", label + " " + result); return; }
        System.out.println("\r" + CL + "  " + w(label) + " " + g(result) + " " + g(CHK));
        showCursor();
        log("OK", label + " " + result);
    }

    public void countdown() { countdownFrames(COUNTDOWN_FRAMES); }
    public void countdownSeconds(int seconds) { countdownFrames(seconds * 20); }

    private void countdownFrames(int frames) {
        if (tier < 2) { sleep(frames * 50); return; }
        hideCursor();
        for (int i = frames; i >= 0; i--) {
            double t = i / (double) frames;
            int w = (int) (t * t * COUNTDOWN_WIDTH);
            System.out.print((i == frames ? "\n  " : "\r  ") + p(BLK.repeat(w)) + " ".repeat(COUNTDOWN_WIDTH - w));
            System.out.flush();
            sleep(50);
        }
        System.out.print("\r" + " ".repeat(COUNTDOWN_WIDTH + 4) + "\r");
        showCursor();
    }

    public void hideCursor() {
        if (tier >= 2) {
            System.out.print("\u001B[?25l");
            System.out.flush();
        }
    }

    public void showCursor() {
        if (tier >= 2) {
            System.out.print("\u001B[?25h");
            System.out.flush();
        }
    }

    public void close() {
        log.close();
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
