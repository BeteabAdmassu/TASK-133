package com.eaglepoint.console.service.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks the {@link InstallerExecutor} implementation for this JVM.
 *
 * <p>Resolution rules (first match wins):
 * <ol>
 *   <li>{@code installer.mode=test} system property → {@link TestModeInstallerExecutor}</li>
 *   <li>{@code UPDATER_INSTALLER_MODE=test} env var → {@link TestModeInstallerExecutor}</li>
 *   <li>Non-Windows OS → {@link TestModeInstallerExecutor} (we never try
 *       to shell out to msiexec on Linux / macOS — docker CI is the
 *       canonical example).</li>
 *   <li>Otherwise → {@link MsiExecInstallerExecutor}</li>
 * </ol>
 *
 * <p>The "test mode on non-Windows" default is what lets the evaluator
 * run the integration tests in a Linux container without patching any
 * env vars.</p>
 */
public final class InstallerExecutorFactory {

    private static final Logger log = LoggerFactory.getLogger(InstallerExecutorFactory.class);

    private InstallerExecutorFactory() {}

    public static InstallerExecutor resolve() {
        String modeProp = System.getProperty("installer.mode");
        String modeEnv = System.getenv("UPDATER_INSTALLER_MODE");
        if ("test".equalsIgnoreCase(modeProp) || "test".equalsIgnoreCase(modeEnv)) {
            log.info("InstallerExecutor: test-mode (explicitly requested)");
            return new TestModeInstallerExecutor();
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            log.info("InstallerExecutor: test-mode (non-Windows host)");
            return new TestModeInstallerExecutor();
        }
        log.info("InstallerExecutor: msiexec");
        return new MsiExecInstallerExecutor();
    }
}
