package com.eaglepoint.console.service.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Deterministic installer executor used in CI and on non-Windows
 * development hosts.  Never shells out — instead it writes a simulated
 * installer log to {@code logPath}, records every invocation on an
 * internal list so tests can assert against it, and returns a
 * caller-configurable {@link Result}.
 *
 * <p>Default behaviour is "success with exit 0" so a plain
 * {@code new TestModeInstallerExecutor()} is usable as a no-op in
 * integration tests.  Tests that want to simulate an installer failure
 * call {@link #setNextExitCode(int)} to override the next result.</p>
 */
public class TestModeInstallerExecutor implements InstallerExecutor {

    public enum InvocationKind { INSTALL, UNINSTALL }

    public static final class Invocation {
        public final InvocationKind kind;
        public final String target;     // msi file path or product code
        public final List<String> properties;
        public final Path logPath;

        public Invocation(InvocationKind kind, String target, List<String> properties, Path logPath) {
            this.kind = kind;
            this.target = target;
            this.properties = properties == null ? List.of() : List.copyOf(properties);
            this.logPath = logPath;
        }
    }

    private final List<Invocation> invocations = new CopyOnWriteArrayList<>();
    /** Next exit code to return, then reset to 0. */
    private volatile Integer nextExitCode = null;
    private volatile String nextSummary = null;

    @Override public String mode() { return "test-mode"; }

    /** Override the result of the next {@code install}/{@code uninstall} call. */
    public void setNextExitCode(int exitCode) {
        this.nextExitCode = exitCode;
    }

    public void setNextSummary(String summary) {
        this.nextSummary = summary;
    }

    public List<Invocation> getInvocations() {
        return new ArrayList<>(invocations);
    }

    public void clear() {
        invocations.clear();
        nextExitCode = null;
        nextSummary = null;
    }

    @Override
    public Result install(Path msiFile, List<String> properties, Path logPath, Duration timeout) {
        invocations.add(new Invocation(InvocationKind.INSTALL,
            msiFile.toString(), properties, logPath));
        writeSimulatedLog(logPath, "[test-mode] install " + msiFile);
        return nextResult("test install " + msiFile.getFileName());
    }

    @Override
    public Result uninstall(String productCode, Path logPath, Duration timeout) {
        invocations.add(new Invocation(InvocationKind.UNINSTALL,
            productCode, null, logPath));
        writeSimulatedLog(logPath, "[test-mode] uninstall " + productCode);
        return nextResult("test uninstall " + productCode);
    }

    private Result nextResult(String fallbackSummary) {
        int exit = nextExitCode == null ? 0 : nextExitCode;
        String summary = nextSummary == null ? fallbackSummary + " exit=" + exit : nextSummary;
        // Reset so subsequent calls default back to success — tests can
        // set the override per-call without leaking state.
        nextExitCode = null;
        nextSummary = null;
        return new Result(exit, summary, 5L);
    }

    private void writeSimulatedLog(Path logPath, String body) {
        if (logPath == null) return;
        try {
            Files.createDirectories(logPath.getParent());
            Files.writeString(logPath, body + "\n", java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logs in test mode are best-effort.
        }
    }
}
