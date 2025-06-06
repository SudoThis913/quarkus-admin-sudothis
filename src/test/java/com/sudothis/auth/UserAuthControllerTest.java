// File: src/test/java/com/sudothis/auth/UserAuthControllerTest.java

package com.sudothis.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class UserAuthControllerTest {

    @Test
    public void testLoginWithInvalidCredentials() {
        given()
          .contentType(ContentType.JSON)
          .body("{\"username\":\"invalid\",\"password\":[\"x\"]}")
          .when()
          .post("/auth/login")
          .then()
          .statusCode(401);
    }

    @Test
    public void testWhoamiWithoutSession() {
        given()
          .when()
          .get("/auth/whoami")
          .then()
          .statusCode(401);
    }

    @Test
    public void testLogoutWithoutSession() {
        given()
          .when()
          .post("/auth/logout")
          .then()
          .statusCode(204);
    }

    @Test
    public void testRefreshWithoutSession() {
        given()
          .when()
          .post("/auth/refresh")
          .then()
          .statusCode(401);
    }

    // Additional tests should include:
    // - testLoginWithValidCredentials (requires user mocking or DB preload)
    // - testRefreshWithValidSession
    // - testWhoamiWithValidSession
    // These will need dependency injection or mocking of Redis and UserService
}
