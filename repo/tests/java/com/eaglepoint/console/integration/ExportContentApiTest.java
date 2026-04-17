package com.eaglepoint.console.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.hamcrest.Matchers.*;

/**
 * Verifies the export pipeline writes <strong>real</strong> entity-driven rows
 * (not placeholder blobs) and that sensitive fields are masked by default.
 *
 * <p>Runs inside the test container — the export destination is a temporary
 * directory on the container filesystem, which the test then reads back to
 * assert content.</p>
 */
class ExportContentApiTest extends BaseIntegrationTest {

    @Test
    void communitiesCsvExportIncludesSeededRowsAndNoPlaceholder() throws Exception {
        // Create a deterministic community so we know what to look for.
        String name = unique("ExportTest");
        withAdmin()
            .body(Map.of("name", name, "description", "exp"))
        .when().post("/api/communities").then().statusCode(201);

        Path outDir = Files.createTempDirectory("exp-content-");
        int jobId = withAdmin()
            .body(Map.of(
                "type", "CSV",
                "entityType", "COMMUNITIES",
                "destinationPath", outDir.toString()
            ))
        .when().post("/api/exports")
        .then().statusCode(201)
            .extract().path("export.id");

        Path csv = waitForCompletedExport(jobId);
        String content = Files.readString(csv);

        // Header must reflect real entity columns, not the old placeholder shape.
        if (!content.contains("name") || !content.contains("status")) {
            throw new AssertionError("Export header missing real columns: " + content);
        }
        if (content.contains("export_type") && content.contains("job_id") && content.contains("generated_at")) {
            throw new AssertionError("Export still emits placeholder columns: " + content);
        }
        if (!content.contains(name)) {
            throw new AssertionError("Seeded community " + name + " not in export content: " + content);
        }
    }

    @Test
    void usersCsvExportMasksStaffIdAndOmitsPasswordHash() throws Exception {
        Path outDir = Files.createTempDirectory("exp-users-");
        int jobId = withAdmin()
            .body(Map.of(
                "type", "CSV",
                "entityType", "USERS",
                "destinationPath", outDir.toString()
            ))
        .when().post("/api/exports")
        .then().statusCode(201)
            .extract().path("export.id");

        Path csv = waitForCompletedExport(jobId);
        String content = Files.readString(csv);

        if (content.toLowerCase().contains("passwordhash") || content.contains("$2a$")) {
            throw new AssertionError("Password hash leaked in users export: " + content);
        }
        if (!content.contains("[MASKED]")) {
            throw new AssertionError("staffId should appear as [MASKED] in users export: " + content);
        }
        if (!content.contains("admin")) {
            throw new AssertionError("Expected seed admin username in users export: " + content);
        }
    }

    @Test
    void pickupPointsCsvExportMasksAddress() throws Exception {
        // Seed a community + pickup point so export has a row.
        int communityId = withAdmin()
            .body(Map.of("name", unique("ExpPPCommunity")))
        .when().post("/api/communities").then().statusCode(201)
            .extract().path("community.id");

        withAdmin()
            .body(Map.of(
                "communityId", communityId,
                "address", "999 Should Be Masked St",
                "zipCode", "12345",
                "streetRangeStart", "1",
                "streetRangeEnd", "9999",
                "hoursJson", "{}",
                "capacity", 5
            ))
        .when().post("/api/pickup-points").then().statusCode(201);

        Path outDir = Files.createTempDirectory("exp-pp-");
        int jobId = withAdmin()
            .body(Map.of(
                "type", "CSV",
                "entityType", "PICKUP_POINTS",
                "destinationPath", outDir.toString()
            ))
        .when().post("/api/exports")
        .then().statusCode(201)
            .extract().path("export.id");

        Path csv = waitForCompletedExport(jobId);
        String content = Files.readString(csv);

        if (content.contains("999 Should Be Masked St")) {
            throw new AssertionError("Address leaked plaintext in export: " + content);
        }
        if (!content.contains("[MASKED]")) {
            throw new AssertionError("Expected [MASKED] marker for address in pickup-point export");
        }
    }

    @Test
    void exportWithUnsupportedEntityTypeMarksJobFailed() throws Exception {
        Path outDir = Files.createTempDirectory("exp-bad-");
        int jobId = withAdmin()
            .body(Map.of(
                "type", "CSV",
                "entityType", "NOT_A_REAL_ENTITY",
                "destinationPath", outDir.toString()
            ))
        .when().post("/api/exports")
        .then().statusCode(201)
            .extract().path("export.id");

        // Job should eventually transition to FAILED (async worker picks it up).
        for (int i = 0; i < 50; i++) {
            String status = withAdmin().when().get("/api/exports/" + jobId).then()
                .statusCode(200).extract().path("export.status");
            if ("FAILED".equals(status)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Unsupported-entity export did not FAIL within 5s");
    }

    private Path waitForCompletedExport(int jobId) throws Exception {
        for (int i = 0; i < 50; i++) {
            String status = withAdmin().when().get("/api/exports/" + jobId).then()
                .statusCode(200).extract().path("export.status");
            if ("COMPLETED".equals(status)) {
                String path = withAdmin().when().get("/api/exports/" + jobId).then()
                    .extract().path("export.outputFilePath");
                if (path == null) throw new AssertionError("Completed export missing outputPath");
                return Path.of(path);
            }
            if ("FAILED".equals(status)) {
                String reason = withAdmin().when().get("/api/exports/" + jobId).then()
                    .extract().path("export.errorMessage");
                throw new AssertionError("Export job " + jobId + " failed: " + reason);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Export " + jobId + " did not complete within 5s");
    }
}
