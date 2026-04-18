package com.eaglepoint.console.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Signature;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * HTTP-level verification of the .msi apply/rollback orchestration via
 * {@code /api/updates/*}.  The embedded server is booted with
 * {@code installer.mode=test} so valid signed packages produce a real
 * {@code INSTALLED} history row without shelling out to any real
 * installer.
 *
 * <p>Each test clears {@code update_history} + the {@code installed/} and
 * {@code backups/} directories before running so assertions hit exact
 * values (no "200 or 409" ambiguity).  Package names are also per-test
 * unique to avoid cross-test collisions inside {@code incoming/}.</p>
 */
class UpdateInstallerApiTest extends BaseIntegrationTest {

    private static final Path INCOMING = UPDATER_DIR.resolve("incoming");

    @BeforeEach
    void clearBeforeEach() {
        clearUpdaterFixture();
    }

    @Test
    void applyValidSignedPackageReturnsInstalledRowAndInvokesTestInstaller() throws Exception {
        String name = "eaglepoint-3.0.0";
        dropMsiPackage(name, "3.0.0",
            "{11111111-1111-1111-1111-111111111111}", List.of("INSTALLDIR=C:\\EaglePoint"));

        withAdmin()
        .when()
            .post("/api/updates/packages/" + name + "/apply")
        .then()
            .statusCode(200)
            .body("update.action", equalTo("INSTALLED"))
            .body("update.status", equalTo("SUCCESS"))
            .body("update.installerType", equalTo("MSI"))
            .body("update.exitCode", equalTo(0))
            .body("update.logPath", notNullValue())
            .body("update.toVersion", equalTo("3.0.0"))
            .body("update.signatureStatus", equalTo("VALID"))
            .body("update.recoveryState", nullValue());
    }

    @Test
    void applyRejectedWhenSignatureTampered() throws Exception {
        String name = "eaglepoint-3.1.0";
        dropMsiPackage(name, "3.1.0",
            "{22222222-2222-2222-2222-222222222222}", List.of());
        Path mf = INCOMING.resolve(name).resolve("manifest.json");
        Files.writeString(mf, Files.readString(mf).replace("3.1.0", "9.9.9"));

        withAdmin()
        .when()
            .post("/api/updates/packages/" + name + "/apply")
        .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_ERROR"))
            .body("error.message", containsString("SIGNATURE_INVALID"));
    }

    @Test
    void applyRejectsLegacyPackageWithoutInstallerType() throws Exception {
        // In production strict-MSI mode, a package with no installerType
        // must be rejected before any installer runs.
        String name = "legacy-4.0.0";
        dropLegacyPayloadPackage(name, "4.0.0");

        withAdmin()
        .when()
            .post("/api/updates/packages/" + name + "/apply")
        .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_ERROR"))
            .body("error.message", containsString("installerType=MSI"))
            .body("error.fields.installerType", containsString("installerType=MSI"));
    }

    @Test
    void applyRejectsUnsupportedInstallerType() throws Exception {
        String name = "eaglepoint-4.1.0";
        dropMsiPackage(name, "4.1.0",
            "{44444444-4444-4444-4444-444444444444}", List.of());
        // Re-write the manifest with an unsupported installerType and
        // sign it with the test key so signature verification passes but
        // the installer-type check rejects.
        Path pkg = INCOMING.resolve(name);
        Path mf = pkg.resolve("manifest.json");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = new ObjectMapper().readValue(Files.readAllBytes(mf), Map.class);
        m.put("installerType", "EXE");
        byte[] newBody = new ObjectMapper().writeValueAsBytes(m);
        Files.write(mf, newBody);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(updaterSigningKeys().getPrivate());
        signer.update(newBody);
        Files.write(pkg.resolve("manifest.sig"), signer.sign());

        withAdmin()
        .when()
            .post("/api/updates/packages/" + name + "/apply")
        .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_ERROR"))
            .body("error.message", allOf(
                containsString("Unsupported installerType"),
                containsString("EXE")))
            .body("error.fields.installerType", containsString("EXE"));
    }

    @Test
    void rollbackRequiresSystemAdmin() {
        anonymous().when().post("/api/updates/rollback").then().statusCode(401);
        asRole("OPS_MANAGER").when().post("/api/updates/rollback").then().statusCode(403);
    }

    @Test
    void rollbackAfterTwoAppliesRestoresPreviousVersion() throws Exception {
        String v1 = "eaglepoint-3.2.0";
        String v2 = "eaglepoint-3.3.0";
        dropMsiPackage(v1, "3.2.0",
            "{33333333-3333-3333-3333-333333333333}", List.of());
        dropMsiPackage(v2, "3.3.0",
            "{44444444-4444-4444-4444-444444444444}", List.of());

        withAdmin().when().post("/api/updates/packages/" + v1 + "/apply")
            .then().statusCode(200);
        withAdmin().when().post("/api/updates/packages/" + v2 + "/apply")
            .then().statusCode(200);

        withAdmin()
        .when()
            .post("/api/updates/rollback")
        .then()
            .statusCode(200)
            .body("update.action", equalTo("ROLLED_BACK"))
            .body("update.status", equalTo("SUCCESS"))
            .body("update.fromVersion", equalTo("3.3.0"))
            .body("update.toVersion", equalTo("3.2.0"))
            .body("update.logPath", notNullValue());
    }

    @Test
    void applyWithUnsafeInstallArgsRejectedWithStructuredError() throws Exception {
        String name = "eaglepoint-3.4.0";
        dropMsiPackage(name, "3.4.0",
            "{55555555-5555-5555-5555-555555555555}",
            List.of("INSTALLDIR=C:\\ok; rm -rf /"));

        withAdmin()
        .when()
            .post("/api/updates/packages/" + name + "/apply")
        .then()
            .statusCode(400)
            .body("error.code", equalTo("VALIDATION_ERROR"))
            .body("error.message", containsString("unsafe"))
            .body("error.fields.installArgs", containsString("unsafe"));
    }

    @Test
    void packageListIncludesInstallerFields() throws Exception {
        String name = "eaglepoint-3.5.0";
        dropMsiPackage(name, "3.5.0",
            "{66666666-6666-6666-6666-666666666666}", List.of("ENVIRONMENT=prod"));

        withAdmin()
        .when()
            .get("/api/updates/packages")
        .then()
            .statusCode(200)
            .body("data.findAll { it.packageName == '" + name + "' }.size()", equalTo(1))
            .body("data.find { it.packageName == '" + name + "' }.manifest.installerType",
                equalTo("MSI"))
            .body("data.find { it.packageName == '" + name + "' }.manifest.version",
                equalTo("3.5.0"));
    }

    @Test
    void applyRequiresSystemAdmin() throws Exception {
        String name = "eaglepoint-3.6.0";
        dropMsiPackage(name, "3.6.0",
            "{77777777-7777-7777-7777-777777777777}", List.of());

        asRole("OPS_MANAGER").when()
            .post("/api/updates/packages/" + name + "/apply")
        .then().statusCode(403);
        anonymous().when()
            .post("/api/updates/packages/" + name + "/apply")
        .then().statusCode(401);
    }

    @Test
    void historyRecordsInstallerTypeAndExitCodeForSuccessfulApply() throws Exception {
        String name = "eaglepoint-3.7.0";
        dropMsiPackage(name, "3.7.0",
            "{88888888-8888-8888-8888-888888888888}", List.of());

        withAdmin().when().post("/api/updates/packages/" + name + "/apply")
            .then().statusCode(200);

        withAdmin()
        .when()
            .get("/api/updates/history")
        .then()
            .statusCode(200)
            .body("data[0].action", equalTo("INSTALLED"))
            .body("data[0].status", equalTo("SUCCESS"))
            .body("data[0].installerType", equalTo("MSI"))
            .body("data[0].exitCode", equalTo(0))
            .body("data[0].logPath", notNullValue())
            .body("data[0].recoveryState", nullValue());
    }

    @Test
    void currentInstalledReflectsLatestSuccessfulApply() throws Exception {
        String name = "eaglepoint-3.8.0";
        dropMsiPackage(name, "3.8.0",
            "{99999999-9999-9999-9999-999999999999}", List.of());

        withAdmin().when().post("/api/updates/packages/" + name + "/apply")
            .then().statusCode(200);

        withAdmin()
        .when()
            .get("/api/updates/current")
        .then()
            .statusCode(200)
            .body("current.toVersion", equalTo("3.8.0"))
            .body("current.action", equalTo("INSTALLED"))
            .body("current.status", equalTo("SUCCESS"));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void dropMsiPackage(String name, String version, String productCode,
                                 List<String> installArgs) throws Exception {
        Path pkg = INCOMING.resolve(name);
        Files.createDirectories(pkg);
        Path msi = pkg.resolve(name + ".msi");
        Files.writeString(msi, "fake-msi-" + version);
        String sha = DigestUtils.sha256Hex(Files.readAllBytes(msi));

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", version);
        manifest.put("installerType", "MSI");
        manifest.put("installerFile", name + ".msi");
        manifest.put("payloadFilename", name + ".msi");
        manifest.put("payloadSha256", sha);
        manifest.put("payloadSize", Files.size(msi));
        manifest.put("productCode", productCode);
        manifest.put("installArgs", installArgs);
        manifest.put("signingKeyId", "eaglepoint-release-itest");
        byte[] body = new ObjectMapper().writeValueAsBytes(manifest);
        Files.write(pkg.resolve("manifest.json"), body);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(updaterSigningKeys().getPrivate());
        signer.update(body);
        Files.write(pkg.resolve("manifest.sig"), signer.sign());
    }

    /** Legacy payload-only package — no installerType, used to verify strict-MSI rejection. */
    private void dropLegacyPayloadPackage(String name, String version) throws Exception {
        Path pkg = INCOMING.resolve(name);
        Files.createDirectories(pkg);
        Path payload = pkg.resolve("payload.zip");
        Files.writeString(payload, "legacy-payload-" + version);
        String sha = DigestUtils.sha256Hex(Files.readAllBytes(payload));

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", version);
        // NOTE: no installerType on purpose — legacy path.
        manifest.put("payloadFilename", "payload.zip");
        manifest.put("payloadSha256", sha);
        manifest.put("payloadSize", Files.size(payload));
        manifest.put("signingKeyId", "eaglepoint-release-itest");
        byte[] body = new ObjectMapper().writeValueAsBytes(manifest);
        Files.write(pkg.resolve("manifest.json"), body);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(updaterSigningKeys().getPrivate());
        signer.update(body);
        Files.write(pkg.resolve("manifest.sig"), signer.sign());
    }
}
