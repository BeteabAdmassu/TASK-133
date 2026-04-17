package com.eaglepoint.console.integration;

import com.eaglepoint.console.api.ApiServer;
import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.config.DatabaseConfig;
import com.eaglepoint.console.config.LoggingConfig;
import com.eaglepoint.console.config.SecurityConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;

/**
 * Shared fixture for REST-Assured integration tests.
 *
 * <p>The embedded Javalin server is started <strong>once per JVM</strong> on the
 * first test class that extends this base (guarded by {@link #SERVER_STARTED}).
 * This avoids the re-init issues that occur when global singletons (JobScheduler,
 * NotificationService, DatabaseConfig HikariPool) are torn down and re-created
 * between test classes running in the same surefire fork.</p>
 *
 * <p>Tests get per-role bearer tokens via {@link #tokenFor(String)}.  Role tokens
 * are cached for the JVM lifetime but refreshed lazily if cleared.  Each test
 * method is expected to create its own deterministic fixtures using {@link #unique(String)}.</p>
 */
public abstract class BaseIntegrationTest {

    protected static final int TEST_PORT = 18080;
    private static final AtomicBoolean SERVER_STARTED = new AtomicBoolean(false);
    private static final Map<String, String> TOKEN_CACHE = new HashMap<>();

    /**
     * Shared Ed25519 keypair for integration tests that need to sign
     * update-package manifests.  Generated eagerly at class load so the
     * public key lands in {@code updater.public.key} BEFORE the
     * {@link ApiServer} constructs the signature verifier.  Tests access
     * the private key via {@link #updaterSigningKeys()}.
     */
    private static final KeyPair UPDATER_KEYS;
    /** Updater working dir for integration tests (incoming/ installed/ backups/ logs/). */
    protected static final Path UPDATER_DIR;

    static {
        try {
            UPDATER_KEYS = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            String spki = Base64.getEncoder().encodeToString(UPDATER_KEYS.getPublic().getEncoded());
            System.setProperty("updater.public.key", spki);
            UPDATER_DIR = Paths.get("updater-itest").toAbsolutePath();
            // Wipe any packages/state left behind by a prior test run so the
            // new Ed25519 keypair actually matches the signatures on disk.
            if (Files.exists(UPDATER_DIR)) {
                try (var walk = Files.walk(UPDATER_DIR)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
                }
            }
            Files.createDirectories(UPDATER_DIR.resolve("incoming"));
            System.setProperty("updater.dir", UPDATER_DIR.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyPair updaterSigningKeys() {
        return UPDATER_KEYS;
    }

    /** Demo credentials as seeded by V2__seed_admin_user.sql. */
    protected static final Map<String, String> CREDENTIALS = Map.of(
        "SYSTEM_ADMIN",    "admin:Admin1234!",
        "OPS_MANAGER",     "ops_manager:Manager1234!",
        "REVIEWER",        "reviewer:Reviewer1234!",
        "AUDITOR",         "auditor:Auditor1234!",
        "DATA_INTEGRATOR", "data_integrator:Integrator1234!"
    );

    @BeforeAll
    static void startServerOnce() throws Exception {
        if (!SERVER_STARTED.compareAndSet(false, true)) {
            return;
        }
        System.setProperty("app.headless", "true");
        System.setProperty("api.port", String.valueOf(TEST_PORT));
        System.setProperty("db.path", ":memory:");
        // 32-byte AES key matching V2__seed_admin_user.sql ciphertext.
        System.setProperty("APP_TEST_ENC_KEY",
            "ZWFnbGVwb2ludC1jb25zb2xlLXNlZWQta2V5LXYxISE=");
        // Tests share a single JVM/server, so a 60/min business limit is far
        // too tight for hundreds of HTTP calls across the suite.  The live
        // container still uses the default 60/min — this override only affects
        // the embedded server started by the tests.
        System.setProperty("rate.limit.max", "10000");
        // Force the updater to use the deterministic test-mode installer so
        // integration tests never shell out to a real msiexec / subprocess.
        System.setProperty("installer.mode", "test");

        LoggingConfig.init();
        AppConfig.init();
        SecurityConfig.initHeadless();
        DatabaseConfig.init();
        ApiServer.start(TEST_PORT);

        RestAssured.baseURI = "http://127.0.0.1";
        RestAssured.port = TEST_PORT;

        // Register a JVM shutdown hook so the server is torn down cleanly when the
        // surefire fork exits — AfterAll-per-class would kill the shared server.
        Runtime.getRuntime().addShutdownHook(new Thread(ApiServer::stop, "itest-server-stop"));
    }

    /**
     * Login with a seeded role and return the raw bearer token.  Cached per JVM.
     *
     * <p>Login revokes any prior token for the same user, so tests that do
     * their own explicit login/logout calls MUST call
     * {@link #invalidateTokenCache()} (or {@link #invalidateTokenFor(String)})
     * afterwards so the next {@code tokenFor} gets a fresh valid token.</p>
     */
    protected static synchronized String tokenFor(String role) {
        String cached = TOKEN_CACHE.get(role);
        if (cached != null) return cached;
        String creds = CREDENTIALS.get(role);
        if (creds == null) throw new IllegalArgumentException("Unknown role: " + role);
        String[] parts = creds.split(":", 2);
        Response resp = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", parts[0], "password", parts[1]))
            .when()
            .post("/api/auth/login");
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Failed to login as " + role + ": HTTP "
                + resp.statusCode() + " " + resp.asString());
        }
        String token = resp.jsonPath().getString("token");
        TOKEN_CACHE.put(role, token);
        return token;
    }

    /** Drop the cached token for a single role; next {@link #tokenFor} re-logs-in. */
    protected static synchronized void invalidateTokenFor(String role) {
        TOKEN_CACHE.remove(role);
    }

    /** Invalidate all cached role tokens (call after a logout test that revokes tokens). */
    protected static synchronized void invalidateTokenCache() {
        TOKEN_CACHE.clear();
    }

    /** RequestSpecification authenticated as admin (SYSTEM_ADMIN). */
    protected static RequestSpecification withAdmin() {
        return given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tokenFor("SYSTEM_ADMIN"));
    }

    /** RequestSpecification authenticated as the given role. */
    protected static RequestSpecification asRole(String role) {
        return given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tokenFor(role));
    }

    /** Anonymous (no Authorization header) — use for 401 assertions. */
    protected static RequestSpecification anonymous() {
        return given().contentType(ContentType.JSON);
    }

    /** Appends a deterministic unique suffix so tests can run in any order without collisions. */
    protected static String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    /**
     * Compatibility shim: some older tests reference {@code adminToken} directly.
     * Delegates to {@link #tokenFor(String)} for SYSTEM_ADMIN.
     */
    protected static String adminToken() {
        return tokenFor("SYSTEM_ADMIN");
    }
}
