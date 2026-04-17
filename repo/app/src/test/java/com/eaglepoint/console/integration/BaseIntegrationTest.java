package com.eaglepoint.console.integration;

import com.eaglepoint.console.api.ApiServer;
import com.eaglepoint.console.config.AppConfig;
import com.eaglepoint.console.config.DatabaseConfig;
import com.eaglepoint.console.config.LoggingConfig;
import com.eaglepoint.console.config.SecurityConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;

import static io.restassured.RestAssured.given;

public abstract class BaseIntegrationTest {

    protected static String adminToken;
    protected static final int TEST_PORT = 18080;

    @BeforeAll
    static void startServer() throws Exception {
        System.setProperty("app.headless", "true");
        System.setProperty("api.port", String.valueOf(TEST_PORT));
        System.setProperty("db.path", ":memory:");
        System.setProperty("APP_TEST_ENC_KEY",
            "dGVzdC1rZXktMzItYnl0ZXMtbG9uZy0hISE=");

        LoggingConfig.init();
        AppConfig.init();
        SecurityConfig.initHeadless();
        DatabaseConfig.init();
        ApiServer.start(TEST_PORT);

        RestAssured.baseURI = "http://127.0.0.1";
        RestAssured.port = TEST_PORT;

        // Login as admin to get token
        Response loginResp = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "admin", "password", "Admin1234!"))
            .when()
            .post("/api/auth/login");

        if (loginResp.statusCode() == 200) {
            adminToken = loginResp.jsonPath().getString("token");
        }
    }

    @AfterAll
    static void stopServer() {
        ApiServer.stop();
    }

    protected static io.restassured.specification.RequestSpecification withAdmin() {
        return given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken);
    }
}
