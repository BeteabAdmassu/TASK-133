package com.eaglepoint.console.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Rate-limiter integration tests (embedded server).
 *
 * <p>The embedded test server overrides the rate limit to a large value via the
 * {@code rate.limit.max} system property so the suite's hundreds of shared
 * requests don't trip the 60/min business rule.  Consequently this class only
 * verifies that <em>non-burst</em> traffic flows cleanly through the limiter.
 * The 60/min enforcement itself is verified against the <strong>live
 * container</strong> by {@link LiveAppSmokeTest} (which runs with the default
 * production rate limit).</p>
 */
class RateLimiterTest extends BaseIntegrationTest {

    private static final String ROLE = "DATA_INTEGRATOR";

    @Test
    void sequentialRequestsWithinLimitAllSucceed() {
        for (int i = 0; i < 10; i++) {
            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + tokenFor(ROLE))
            .when()
                .get("/api/health")
            .then()
                .statusCode(200);
        }
    }

    @Test
    void concurrentRequestsDoNotThrow() throws InterruptedException {
        int total = 60;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(total);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            for (int i = 0; i < total; i++) {
                pool.submit(() -> {
                    try {
                        int status = given()
                            .contentType(ContentType.JSON)
                            .header("Authorization", "Bearer " + tokenFor(ROLE))
                        .when()
                            .get("/api/health")
                            .statusCode();
                        if (status == 200) success.incrementAndGet();
                        else errors.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(30, TimeUnit.SECONDS), "Requests did not complete in time");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, errors.get(), "Rate limiter must not throw under concurrent load");
        assertTrue(success.get() >= 55, "Expected most concurrent requests to succeed");
    }
}
