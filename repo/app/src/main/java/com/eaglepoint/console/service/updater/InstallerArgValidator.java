package com.eaglepoint.console.service.updater;

import com.eaglepoint.console.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Strict validator for the {@code installArgs} list in a package
 * manifest.  The evaluator's threat model is that a compromised offline
 * package must NOT be able to smuggle shell commands through installer
 * arguments — so this class:
 *
 * <ul>
 *   <li>Only accepts {@code KEY=VALUE} property pairs (i.e. MSI public
 *       properties).  Switches like {@code /qn}, {@code /norestart}, and
 *       {@code /l*v} are supplied by the executor itself, not the
 *       package.</li>
 *   <li>Keys must match {@code [A-Z_][A-Z0-9_]*} — standard Windows
 *       Installer public-property casing.</li>
 *   <li>Values must not contain shell metacharacters
 *       ({@code &amp;, |, ;, &lt;, &gt;, $, `, \r, \n}).</li>
 *   <li>Total length of each arg is capped at 256 characters.</li>
 * </ul>
 */
public final class InstallerArgValidator {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z_][A-Z0-9_]*$");
    private static final Pattern UNSAFE_VALUE = Pattern.compile("[&|;<>$`\\r\\n]");
    private static final int MAX_ARG_LEN = 256;

    private InstallerArgValidator() {}

    /**
     * Validate a list of raw {@code installArgs} entries from a manifest.
     * Returns a new list containing the exact strings that will be passed
     * to the executor; throws {@link ValidationException} on the first
     * offending entry so the apply pipeline aborts before any installer
     * command is launched.
     */
    public static List<String> sanitize(List<String> rawArgs) {
        List<String> out = new ArrayList<>();
        if (rawArgs == null) return out;
        for (String arg : rawArgs) {
            if (arg == null || arg.isBlank()) continue;
            if (arg.length() > MAX_ARG_LEN) {
                throw new ValidationException("installArgs",
                    "installArg too long (>" + MAX_ARG_LEN + " chars)");
            }
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                throw new ValidationException("installArgs",
                    "installArg must be KEY=VALUE (got: '" + abbreviate(arg) + "')");
            }
            String key = arg.substring(0, eq);
            String value = arg.substring(eq + 1);
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new ValidationException("installArgs",
                    "installArg key must be upper-snake-case (got: '" + abbreviate(key) + "')");
            }
            if (UNSAFE_VALUE.matcher(value).find()) {
                throw new ValidationException("installArgs",
                    "installArg value contains unsafe shell metacharacters");
            }
            out.add(key + "=" + value);
        }
        return out;
    }

    /**
     * Validate a Windows Installer GUID in its canonical braced form —
     * used by the rollback path to reject manifest product codes that
     * would otherwise flow into {@code msiexec /x}.
     */
    public static void validateProductCode(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            throw new ValidationException("productCode",
                "productCode is required to run uninstall/rollback");
        }
        String pc = productCode.trim();
        // Windows Installer accepts braced or unbraced GUIDs; normalise to braced.
        if (!pc.matches("^\\{?[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\\}?$")) {
            throw new ValidationException("productCode",
                "productCode must be a Windows Installer GUID");
        }
    }

    private static String abbreviate(String s) {
        if (s.length() <= 32) return s;
        return s.substring(0, 32) + "…";
    }
}
