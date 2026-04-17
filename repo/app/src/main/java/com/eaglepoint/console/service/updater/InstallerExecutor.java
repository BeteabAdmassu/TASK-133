package com.eaglepoint.console.service.updater;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Abstraction over the Windows Installer command line so the updater can
 * run a real {@code msiexec.exe /i} in production but remain fully
 * deterministic in CI / non-Windows test environments.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Resolving the installer binary (e.g. {@code msiexec.exe}).</li>
 *   <li>Enforcing the timeout — callers must not block forever on a
 *       stuck installer.</li>
 *   <li>Writing the per-operation log to {@code logPath} when supplied.</li>
 *   <li>Never echoing raw command lines with secrets into logs — only
 *       exit codes and high-level status are surfaced to callers.</li>
 * </ul>
 */
public interface InstallerExecutor {

    /**
     * Run a silent install of the given {@code msiFile}, pinning the
     * Windows Installer log to {@code logPath}.  Implementations MUST
     * invoke {@code msiexec.exe /i &lt;msi&gt; /qn /norestart /l*v &lt;log&gt;}
     * plus the sanitized {@code properties} list.
     *
     * @param msiFile     absolute path to the {@code .msi} file
     * @param properties  already-validated {@code KEY=VALUE} property pairs
     * @param logPath     where to write the installer's verbose log
     * @param timeout     hard timeout — the process is killed if exceeded
     */
    Result install(Path msiFile, List<String> properties, Path logPath, Duration timeout);

    /**
     * Uninstall a previously-installed product by its Windows Installer
     * {@code productCode} GUID.  Implementations MUST invoke
     * {@code msiexec.exe /x &lt;productCode&gt; /qn /norestart /l*v &lt;log&gt;}.
     *
     * @param productCode Windows Installer product GUID (e.g.
     *                    {@code {12345678-...}}). Caller is responsible
     *                    for validating the format before invoking.
     */
    Result uninstall(String productCode, Path logPath, Duration timeout);

    /**
     * Name returned in audit logs so the evaluator can trace which
     * executor implementation actually ran (e.g. {@code msiexec} vs.
     * {@code test-mode}).
     */
    String mode();

    /**
     * Non-throwing result: carries exit code, duration, and a short
     * human-readable reason.  Installers routinely exit non-zero for
     * legitimate reasons (reboot required, already installed) so the
     * caller — not this layer — maps exit codes to domain outcomes.
     */
    final class Result {
        private final int exitCode;
        private final String summary;
        private final long durationMs;

        public Result(int exitCode, String summary, long durationMs) {
            this.exitCode = exitCode;
            this.summary = summary;
            this.durationMs = durationMs;
        }

        public int getExitCode() { return exitCode; }
        public String getSummary() { return summary; }
        public long getDurationMs() { return durationMs; }
        public boolean isSuccess() { return exitCode == 0; }
    }
}
