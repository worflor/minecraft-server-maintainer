package dev.woflo.fabric;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock Console implementation for testing.
 * Captures all output without writing to a real terminal or log file.
 * <p>
 * Usage:
 * <pre>
 * MockConsole console = new MockConsole();
 * // ... use console in tests ...
 * assertThat(console.getInfoMessages()).contains("Expected message");
 * </pre>
 */
public class MockConsole extends Console {

    private final List<String> infoMessages = new CopyOnWriteArrayList<>();
    private final List<String> warnMessages = new CopyOnWriteArrayList<>();
    private final List<String> failMessages = new CopyOnWriteArrayList<>();
    private final List<String> detailMessages = new CopyOnWriteArrayList<>();
    private final List<String> progressMessages = new CopyOnWriteArrayList<>();
    private final List<RowState> rowStates = new CopyOnWriteArrayList<>();

    private String lastHeader;
    private boolean dryRunCalled;
    private boolean cursorHidden;
    private int countdownSeconds;

    public MockConsole() throws IOException {
        super(createTempLogFile());
    }

    private static File createTempLogFile() throws IOException {
        Path tempFile = Files.createTempFile("mock-console-", ".log");
        tempFile.toFile().deleteOnExit();
        return tempFile.toFile();
    }

    // ========================================================================
    // Overridden methods to capture output
    // ========================================================================

    @Override
    public void header(String loader, String ver) {
        lastHeader = loader + " " + ver;
    }

    @Override
    public void dryRun() {
        dryRunCalled = true;
    }

    @Override
    public void info(String msg) {
        infoMessages.add(msg);
    }

    @Override
    public void warn(String msg) {
        warnMessages.add(msg);
    }

    @Override
    public void fail(String msg) {
        failMessages.add(msg);
    }

    @Override
    public void detail(String name, String from, String to) {
        detailMessages.add(name + ": " + from + " -> " + to);
    }

    @Override
    public void progress(String msg) {
        progressMessages.add(msg);
    }

    @Override
    public void progressDone(String label, String result) {
        progressMessages.add(label + ": " + result + " (done)");
    }

    @Override
    public void setupRows(String... labels) {
        rowStates.clear();
        for (String label : labels) {
            rowStates.add(new RowState(label));
        }
    }

    @Override
    public void rowProgress(int i, int done, int total) {
        if (i < rowStates.size()) {
            rowStates.get(i).setProgress(done, total);
        }
    }

    @Override
    public void rowDone(int i, String v) {
        if (i < rowStates.size()) {
            rowStates.get(i).setDone(v);
        }
    }

    @Override
    public void rowDoneUpdate(int i, String from, String to) {
        if (i < rowStates.size()) {
            rowStates.get(i).setDoneUpdate(from, to);
        }
    }

    @Override
    public void rowSkip(int i, String reason) {
        if (i < rowStates.size()) {
            rowStates.get(i).setSkipped(reason);
        }
    }

    @Override
    public void rowStatus(int i, String status) {
        if (i < rowStates.size()) {
            rowStates.get(i).setStatus(status);
        }
    }

    @Override
    public void endRows() {
        // No-op for mock
    }

    @Override
    public void blankLine() {
        // No-op for mock
    }

    @Override
    public void checking(String what) {
        progressMessages.add("Checking: " + what);
    }

    @Override
    public void checkDone(String result, boolean ok) {
        progressMessages.add("Check result: " + result + " (" + (ok ? "ok" : "not ok") + ")");
    }

    @Override
    public void countdown() {
        // Skip actual countdown in tests
    }

    @Override
    public void countdownSeconds(int seconds) {
        this.countdownSeconds = seconds;
        // Skip actual countdown in tests
    }

    @Override
    public void hideCursor() {
        cursorHidden = true;
    }

    @Override
    public void showCursor() {
        cursorHidden = false;
    }

    // ========================================================================
    // Accessors for test assertions
    // ========================================================================

    public List<String> getInfoMessages() {
        return List.copyOf(infoMessages);
    }

    public List<String> getWarnMessages() {
        return List.copyOf(warnMessages);
    }

    public List<String> getFailMessages() {
        return List.copyOf(failMessages);
    }

    public List<String> getDetailMessages() {
        return List.copyOf(detailMessages);
    }

    public List<String> getProgressMessages() {
        return List.copyOf(progressMessages);
    }

    public List<RowState> getRowStates() {
        return List.copyOf(rowStates);
    }

    public String getLastHeader() {
        return lastHeader;
    }

    public boolean wasDryRunCalled() {
        return dryRunCalled;
    }

    public boolean isCursorHidden() {
        return cursorHidden;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    /**
     * Checks if any failure message contains the given substring.
     */
    public boolean hasFailure(String substring) {
        return failMessages.stream().anyMatch(m -> m.contains(substring));
    }

    /**
     * Checks if any warning message contains the given substring.
     */
    public boolean hasWarning(String substring) {
        return warnMessages.stream().anyMatch(m -> m.contains(substring));
    }

    /**
     * Checks if any info message contains the given substring.
     */
    public boolean hasInfo(String substring) {
        return infoMessages.stream().anyMatch(m -> m.contains(substring));
    }

    /**
     * Gets all output as a single string (header + all messages).
     */
    public String getOutput() {
        StringBuilder sb = new StringBuilder();
        if (lastHeader != null) {
            sb.append(lastHeader).append("\n");
        }
        for (String msg : infoMessages) {
            sb.append(msg).append("\n");
        }
        for (String msg : warnMessages) {
            sb.append(msg).append("\n");
        }
        for (String msg : progressMessages) {
            sb.append(msg).append("\n");
        }
        return sb.toString();
    }

    /**
     * Checks if dry-run mode was indicated.
     */
    public boolean isDryRunMode() {
        return dryRunCalled;
    }

    /**
     * Clears all captured messages.
     */
    public void clear() {
        infoMessages.clear();
        warnMessages.clear();
        failMessages.clear();
        detailMessages.clear();
        progressMessages.clear();
        rowStates.clear();
        lastHeader = null;
        dryRunCalled = false;
        countdownSeconds = 0;
    }

    // ========================================================================
    // Row State Tracking
    // ========================================================================

    /**
     * Represents the state of a row in the console output.
     */
    public static class RowState {
        private final String label;
        private String status;
        private String value;
        private int progressDone;
        private int progressTotal;
        private boolean done;
        private boolean skipped;
        private String fromVersion;
        private String toVersion;

        public RowState(String label) {
            this.label = label;
        }

        public void setProgress(int done, int total) {
            this.progressDone = done;
            this.progressTotal = total;
            this.status = "progress";
        }

        public void setDone(String value) {
            this.value = value;
            this.done = true;
            this.status = "done";
        }

        public void setDoneUpdate(String from, String to) {
            this.fromVersion = from;
            this.toVersion = to;
            this.done = true;
            this.status = "updated";
        }

        public void setSkipped(String reason) {
            this.value = reason;
            this.skipped = true;
            this.status = "skipped";
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getLabel() {
            return label;
        }

        public String getStatus() {
            return status;
        }

        public String getValue() {
            return value;
        }

        public int getProgressDone() {
            return progressDone;
        }

        public int getProgressTotal() {
            return progressTotal;
        }

        public boolean isDone() {
            return done;
        }

        public boolean isSkipped() {
            return skipped;
        }

        public String getFromVersion() {
            return fromVersion;
        }

        public String getToVersion() {
            return toVersion;
        }

        public boolean wasUpdated() {
            return "updated".equals(status);
        }

        @Override
        public String toString() {
            return "RowState{label='%s', status='%s', value='%s', progress=%d/%d}"
                    .formatted(label, status, value, progressDone, progressTotal);
        }
    }
}
