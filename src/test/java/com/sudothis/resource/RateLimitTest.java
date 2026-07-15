package com.sudothis.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs with a per-user limit of 2/min so the limiter trips within a single
 * test. Six back-to-back requests are used instead of exactly limit+1 to stay
 * robust if the fixed window happens to roll over mid-test.
 */
@QuarkusTest
@TestProfile(RateLimitTest.LowLimitProfile.class)
public class RateLimitTest {

    public static class LowLimitProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("sudothis.ratelimit.per-user-per-minute", "2");
        }
    }

    @Test
    @TestSecurity(user = "ratelimit-user", roles = {"admin"})
    void testPerUserLimitReturns429() {
        boolean sawTooManyRequests = false;
        for (int i = 0; i < 6; i++) {
            var response = given().when().get("/api/admin/data/Company");
            int status = response.statusCode();
            assertTrue(status == 200 || status == 429, "Unexpected status: " + status);
            if (status == 429) {
                sawTooManyRequests = true;
                assertEquals("60", response.header("Retry-After"));
            }
        }
        assertTrue(sawTooManyRequests, "Expected at least one 429 after exceeding the per-user limit");
    }

    @Test
    @TestSecurity(user = "ratelimit-nonadmin-path", roles = {"admin"})
    void testNonAdminPathsAreNotRateLimited() {
        for (int i = 0; i < 6; i++) {
            int status = given().when().get("/api/users/me").statusCode();
            assertNotEquals(429, status, "Non-admin API paths must not be rate limited");
        }
    }
}
