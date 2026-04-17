package com.eaglepoint.console.service.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Real Windows Installer executor.  Runs {@code msiexec.exe} out of
 * {@code %WINDIR%\System32\msiexec.exe} (or {@code msiexec} when on the
 * {@code PATH}) with the standard silent-install switch set:
 *
 * <pre>
 *   msiexec.exe /i &lt;package.msi&gt; /qn /norestart /l*v &lt;log&gt; KEY=VALUE...
 *   msiexec.exe /x &lt;productCode&gt;  /qn /norestart /l*v &lt;log&gt;
 * </pre>
 *
 * <p>Process output is NOT piped — we redirect both stdout and stderr
 * into {@code logPath} so the caller can inspect the real installer log.
 * The property list is already validated by
 * {@link InstallerArgValidator} before this class sees it, so this
 * executor can hand it straight to {@link ProcessBuilder}.</p>
 *
 * <p>The process is killed if the timeout expires — Windows Installer
 * can hang waiting on a stuck uninstall and we'd rather surface that as
 * a FAILED row than let the API caller hang.</p>
 */
public class MsiExecInstallerExecutor implements InstallerExecutor {

    private static final Logger log = LoggerFactory.getLogger(MsiExecInstallerExecutor.class);

    private final String msiexecPath;

    public MsiExecInstallerExecutor() {
        this(resolveMsiexec());
    }

    public MsiExecInstallerExecutor(String msiexecPath) {
        this.msiexecPath = msiexecPath;
    }

    @Override public String mode() { return "msiexec"; }

    @Override
    public Result install(Path msiFile, List<String> properties, Path logPath, Duration timeout) {
        List<String> cmd = new ArrayList<>();
        cmd.add(msiexecPath);
        cmd.add("/i");
        cmd.add(msiFile.toAbsolutePath().toString());
        cmd.addAll(silentFlags(logPath));
        if (properties != null) cmd.addAll(properties);
        return run(cmd, logPath, timeout, "msiexec /i");
    }

    @Override
    public Result uninstall(String productCode, Path logPath, Duration timeout) {
        List<String> cmd = new ArrayList<>();
        cmd.add(msiexecPath);
        cmd.add("/x");
        cmd.add(productCode);
        cmd.addAll(silentFlags(logPath));
        return run(cmd, logPath, timeout, "msiexec /x");
    }

    private List<String> silentFlags(Path logPath) {
        List<String> flags = new ArrayList<>();
        flags.add("/qn");
        flags.add("/norestart");
        if (logPath != null) {
            flags.add("/l*v");
            flags.add(logPath.toAbsolutePath().toString());
        }
        return flags;
    }

    private Result run(List<String> cmd, Path logPath, Duration timeout, String summaryTag) {
        long start = System.nanoTime();
        try {
            if (logPath != null) {
                Files.createDirectories(logPath.getParent());
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            if (logPath != null) {
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            }
            Process p = pb.start();
            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            long duration = (System.nanoTime() - start) / 1_000_000;
            if (!finished) {
                p.destroyForcibly();
                log.warn("{} timed out after {}ms; process killed", summaryTag, duration);
                return new Result(-1, summaryTag + " timed out", duration);
            }
            int exit = p.exitValue();
            log.info("{} completed in {}ms (exit={})", summaryTag, duration, exit);
            return new Result(exit, summaryTag + " exit=" + exit, duration);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            long duration = (System.nanoTime() - start) / 1_000_000;
            log.error("{} failed to launch: {}", summaryTag, e.getClass().getSimpleName());
            return new Result(-2, summaryTag + " launch failed", duration);
        }
    }

    private static String resolveMsiexec() {
        String override = System.getProperty("updater.msiexec.path",
            System.getenv("UPDATER_MSIEXEC_PATH"));
        if (override != null && !override.isBlank()) return override;
        String windir = System.getenv("WINDIR");
        if (windir != null && !windir.isBlank()) {
            return windir + "\\System32\\msiexec.exe";
        }
        return "msiexec.exe";
    }
}
