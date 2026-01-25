package dev.woflo.fabric;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Console {
    private static final String R = "\u001B[0m", P = "\u001B[35m", G = "\u001B[32m", Y = "\u001B[33m";
    private static final String E = "\u001B[31m", A = "\u001B[90m", W = "\u001B[97m", UP = "\u001B[A", CL = "\u001B[2K";
    private static final String H = "\u2500", V = "\u2502", TL = "\u250C", TR = "\u2510", BL = "\u2514", BR = "\u2518";
    private static final String CHK = "\u221A", BLK = "\u2588", SHD = "\u2591";

    private final PrintWriter log;
    private final boolean c;
    private List<String> labels = new ArrayList<>();
    private int rows = 0;

    public Console(File logFile) throws IOException {
        log = new PrintWriter(new FileWriter(logFile, true), true);
        c = System.getenv("TERM") != null || System.getenv("COLORTERM") != null
            || System.getProperty("os.name").toLowerCase().contains("win") || System.console() != null;
    }

    private String p(String s) { return c ? P + s + R : s; }
    private String g(String s) { return c ? G + s + R : s; }
    private String y(String s) { return c ? Y + s + R : s; }
    private String r(String s) { return c ? E + s + R : s; }
    private String a(String s) { return c ? A + s + R : s; }
    private String w(String s) { return c ? W + s + R : s; }

    private void log(String type, String msg) {
        log.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + type + " | " + msg);
    }

    public void header(String loader, String ver) {
        String title = "  Server Maintainer", brand = "woflo  ", sub = "  " + loader + " " + ver;
        System.out.println("\n  " + p(TL + H.repeat(48) + TR));
        System.out.println("  " + p(V) + w(title) + " ".repeat(48 - title.length() - brand.length()) + a(brand) + p(V));
        System.out.println("  " + p(V) + a(sub) + " ".repeat(48 - sub.length()) + p(V));
        System.out.println("  " + p(BL + H.repeat(48) + BR) + "\n");
    }

    public void dryRun() { System.out.println("  " + a("(dry run)") + "\n"); }

    public void setupRows(String... lbl) {
        labels = new ArrayList<>(Arrays.asList(lbl));
        rows = lbl.length;
        for (String l : lbl) System.out.println(fmt(l, a(SHD), ""));
    }

    private String fmt(String label, String status, String value) {
        return "  " + w(String.format("%-12s", label)) + " " + status + "  " + value;
    }

    private void upd(int i, String status, String value) {
        int n = rows - i;
        for (int j = 0; j < n; j++) System.out.print(UP);
        System.out.print("\r" + CL + fmt(labels.get(i), status, value));
        for (int j = 0; j < n; j++) System.out.println();
    }

    public void rowProgress(int i, int done, int total) {
        int f = total > 0 ? (done * 16 / total) : 0;
        upd(i, p(BLK), a("[") + p(BLK.repeat(f) + SHD.repeat(16 - f)) + a("] ") + w(done + "/" + total));
    }

    public void rowDone(int i, String v) { upd(i, g(BLK), w(v) + " " + g(CHK)); log("OK", labels.get(i) + " " + v); }
    public void rowDoneUpdate(int i, String from, String to) { upd(i, g(BLK), a(from) + " -> " + g(to) + " " + g(CHK)); log("Update", labels.get(i) + " " + from + " -> " + to); }
    public void rowSkip(int i, String reason) { upd(i, a(SHD), a(reason)); }

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
        for (int i = 30; i >= 0; i--) {
            System.out.print((i == 30 ? "\n  " : "\r  ") + p(BLK.repeat(i * 52 / 30)) + " ".repeat(52 - i * 52 / 30));
            System.out.flush();
            sleep(50);
        }
        System.out.print("\r" + " ".repeat(56) + "\r"); showCursor();
    }

    public void hideCursor() { if (c) System.out.print("\u001B[?25l"); }
    public void showCursor() { if (c) System.out.print("\u001B[?25h"); }
    public void close() { log.close(); }
    private void sleep(int ms) { try { Thread.sleep(ms); } catch (InterruptedException e) {} }
}
