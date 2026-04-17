package com.eaglepoint.console.integration;

import com.eaglepoint.console.ui.AuthSession;
import com.eaglepoint.console.ui.shared.ApiClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirms that {@link ApiClient#postMultipart} propagates the bearer
 * token from {@link AuthSession}; without this the upload would hit 401
 * regardless of session validity.
 */
class ApiClientMultipartAuthTest extends BaseIntegrationTest {

    @Test
    void multipartIncludesAuthorizationHeader() throws Exception {
        // Arrange: a valid session token and a tiny valid CSV file.
        String token = tokenFor("SYSTEM_ADMIN");
        AuthSession.getInstance().set(
            new com.eaglepoint.console.model.User(), token);

        Path csv = Files.createTempFile("route-auth-test-", ".csv");
        Files.writeString(csv, "checkpoint_name,expected_at,actual_at,lat,lon\n"
            + "CP-1,2024-01-01T10:00:00Z,2024-01-01T10:01:00Z,37.7749,-122.4194\n");

        try {
            // Act: use the real ApiClient to hit the protected multipart endpoint.
            Map<String, Object> resp = ApiClient.getInstance()
                .postMultipart("/api/route-imports", csv);

            // Assert: the request was accepted (2xx) — proves auth header flowed.
            assertNotNull(resp);
            assertTrue(resp.containsKey("import"),
                "Server response should include the created import record");
        } finally {
            Files.deleteIfExists(csv);
            AuthSession.getInstance().clear();
        }
    }

    @Test
    void multipartWithoutTokenReturns401() throws Exception {
        AuthSession.getInstance().clear(); // ensure no token

        Path csv = Files.createTempFile("route-auth-test-", ".csv");
        Files.writeString(csv, "checkpoint_name,expected_at,actual_at,lat,lon\n");

        try {
            java.io.IOException ex = assertThrows(java.io.IOException.class,
                () -> ApiClient.getInstance().postMultipart("/api/route-imports", csv));
            assertTrue(ex.getMessage().contains("401"),
                "Without an auth header the server must reject multipart uploads with 401");
        } finally {
            Files.deleteIfExists(csv);
        }
    }
}
