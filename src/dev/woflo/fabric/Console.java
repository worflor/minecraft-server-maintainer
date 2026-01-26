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
    // Box drawing
    private static final String H = "\u2500", V = "\u2502", TL = "\u250C", TR = "\u2510", BL = "\u2514", BR = "\u2518";
    // Symbols
    private static final String CHK = "\u221A", BLK = "\u2588", SHD = "\u2591";
    private static final int HEADER_WIDTH = 48, BAR_WIDTH = 16, COUNTDOWN_WIDTH = 52, COUNTDOWN_FRAMES = 30;

    private final PrintWriter log;
    private final boolean tty;
    private List<String> labels = new ArrayList<>();
    private String[] rowStatus, rowValue;

    public Console(File logFile) throws IOException {
        log = new PrintWriter(new FileWriter(logFile, true), true);
        tty = System.getenv("TERM") != null || System.getenv("COLORTERM") != null
            || System.getProperty("os.name").toLowerCase().contains("win") || System.console() != null;
    }

    // Color helpers
    private String col(String s, String x) { return tty ? x + s + R : s; }
    private String p(String s) { return col(s, P); }
    private String g(String s) { return col(s, G); }
    private String y(String s) { return col(s, Y); }
    private String r(String s) { return col(s, E); }
    private String a(String s) { return col(s, A); }
    private String w(String s) { return col(s, W); }

    private void log(String type, String msg) {
        log.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + type + " | " + msg);
    }

    public void header(String loader, String ver) {
        String title = "  Server Maintainer", brand = "woflo  ", sub = "  " + loader + " " + ver;
        System.out.println("\n  " + p(TL + H.repeat(HEADER_WIDTH) + TR));
        System.out.println("  " + p(V) + w(title) + " ".repeat(HEADER_WIDTH - title.length() - brand.length()) + a(brand) + p(V));
        System.out.println("  " + p(V) + a(sub) + " ".repeat(HEADER_WIDTH - sub.length()) + p(V));
        System.out.println("  " + p(BL + H.repeat(HEADER_WIDTH) + BR) + "\n");
    }

    public void dryRun() { System.out.println("  " + a("(dry run)") + "\n"); }

    // Initialize rows and save cursor position
    public void setupRows(String... lbl) {
        labels = new ArrayList<>(Arrays.asList(lbl));
        rowStatus = new String[lbl.length];
        rowValue = new String[lbl.length];
        Arrays.fill(rowStatus, a(SHD));
        Arrays.fill(rowValue, "");
        if (tty) System.out.print(SAVE); // save cursor at start of row area
        redrawRows();
    }

    // Redraw all rows from saved position
    private void redrawRows() {
        if (tty) System.out.print(RESTORE); // restore to start of row area
        for (int i = 0; i < labels.size(); i++) {
            if (tty) System.out.print(CL); // clear line
            System.out.println(fmt(labels.get(i), rowStatus[i], rowValue[i]));
        }
        System.out.flush();
    }

    private String fmt(String label, String status, String value) {
        return "  " + w(String.format("%-12s", label)) + " " + status + "  " + value;
    }

    public void rowProgress(int i, int done, int total) {
        if (i >= labels.size()) return;
        int f = total > 0 ? (done * BAR_WIDTH / total) : 0;
        rowStatus[i] = p(BLK);
        rowValue[i] = a("[") + p(BLK.repeat(f) + SHD.repeat(BAR_WIDTH - f)) + a("] ") + w(done + "/" + total);
        redrawRows();
    }

    public void rowDone(int i, String v) {
        if (i >= labels.size()) return;
        rowStatus[i] = g(BLK);
        rowValue[i] = w(v) + " " + g(CHK);
        redrawRows();
        log("OK", labels.get(i) + " " + v);
    }

    public void rowDoneUpdate(int i, String from, String to) {
        if (i >= labels.size()) return;
        rowStatus[i] = g(BLK);
        rowValue[i] = a(from) + " -> " + g(to) + " " + g(CHK);
        redrawRows();
        log("Update", labels.get(i) + " " + from + " -> " + to);
    }

    public void rowSkip(int i, String reason) {
        if (i >= labels.size()) return;
        rowStatus[i] = a(SHD);
        rowValue[i] = a(reason);
        redrawRows();
    }

    // Show a status message in a row (e.g., "backing up", "installing")
    public void rowStatus(int i, String status) {
        if (i >= labels.size()) return;
        rowStatus[i] = y(BLK);
        rowValue[i] = a(status + "...");
        redrawRows();
    }

    // End row mode - cursor is now after rows, safe to print other content
    public void endRows() { }

    public void detail(String name, String from, String to) {
        System.out.println("    " + g(CHK) + " " + w(name.length() <= 18 ? name : name.substring(0, 17) + "~") + " " + a(from + " -> ") + g(to));
        log("Update", name + " " + from + " -> " + to);
    }

    public void blankLine() { System.out.println(); }
    public void info(String msg) { System.out.println("  " + a(msg)); log("Info", msg); }
    public void warn(String msg) { System.out.println("  " + y("! " + msg)); log("WARN", msg); }
    public void fail(String msg) { System.out.println("  " + r("X " + msg)); log("ERROR", msg); }
    public void checking(String what) { System.out.print("  " + a(what + "...")); System.out.flush(); }
    public void checkDone(String result, boolean ok) { System.out.println(" " + (ok ? w(result) + " " + g(CHK) : y(result))); }

    public void countdown() {
        hideCursor();
        for (int i = COUNTDOWN_FRAMES; i >= 0; i--) {
            double t = i / (double) COUNTDOWN_FRAMES; int w = (int)(t * t * COUNTDOWN_WIDTH);
            System.out.print((i == COUNTDOWN_FRAMES ? "\n  " : "\r  ") + p(BLK.repeat(w)) + " ".repeat(COUNTDOWN_WIDTH - w));
            System.out.flush();
            sleep(50);
        }
        System.out.print("\r" + " ".repeat(COUNTDOWN_WIDTH + 4) + "\r"); showCursor();
    }

    public void hideCursor() { if (tty) System.out.print("\u001B[?25l"); }
    public void showCursor() { if (tty) System.out.print("\u001B[?25h"); }
    public void close() { log.close(); }
    private void sleep(int ms) { try { Thread.sleep(ms); } catch (InterruptedException e) {} }
}
