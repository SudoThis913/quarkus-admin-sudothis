// src/test/java/com/sudothis/resource/AppUserResourceTest.java
package com.sudothis.resource;

import com.sudothis.model.AppUser;
import com.sudothis.resource.AppUserResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;

import static io.restassured.RestAssured.given;

/**
 * Happy‑path + validation tests for {@link AppUserResource}.
 */
@QuarkusTest
class AppUserResourceTest {

    @BeforeEach
    @Transactional
    void seedAdmin() {
        if (AppUser.find("username", "admin").firstResult() == null) {
            AppUser.create("admin", "root@local", "changeme".toCharArray(), "admin");
        }
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    void createUser_invalidEmail_returns400() {
        var body = new AppUserResource.CreateUserRequest(
                "bob", "bad-email", "secret", "user");

        given()
            .contentType(ContentType.JSON)
            .body(body)
        .when()
            .post("/api/users")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    void createUser_and_login() {
        // create
        var create = new AppUserResource.CreateUserRequest(
                "alice", "alice@example.com", "hunter2", "user");

        given()
            .contentType(ContentType.JSON)
            .body(create)
        .when()
            .post("/api/users")
        .then()
            .statusCode(201);

        // login
        var login = new AppUserResource.LoginRequest("alice", "hunter2");
        String token =
            given()
                .contentType(ContentType.JSON)
                .body(login)
            .when()
                .post("/api/users/login")
            .then()
                .statusCode(200)
                .body("token", Matchers.notNullValue())
                .extract().path("token");

        given()
            .auth().oauth2(token)
        .when()
            .get("/api/users/me")
        .then()
            .statusCode(200)
            .body("username", Matchers.equalTo("alice"))
            .body("email", Matchers.equalTo("alice@example.com"));
    }
}
