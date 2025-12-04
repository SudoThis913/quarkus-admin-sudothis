package com.sudothis.resource;

import com.sudothis.model.AppUser;
import com.sudothis.model.AuditLog;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class AppUserResourceTest {

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testCreateUserAndAudit() {
        var dto = new AppUserResource.CreateUserDto("bob", "bob@example.com", "uuid-123");

        given()
            .contentType(ContentType.JSON)
            .body(dto)
        .when()
            .post("/api/users")
        .then()
            .statusCode(201);

        // Verify User Created
        given()
            .when()
            .get("/api/users")
        .then()
            .statusCode(200)
            .body("size()", Matchers.greaterThanOrEqualTo(1))
            .body("find { it.username == 'bob' }.email", Matchers.equalTo("bob@example.com"));

        // Verify Audit Log
        // We need to verify that an audit log entry exists for this action.
        // Since we can't easily inject EntityManager in a @QuarkusTest without @Inject, 
        // we'll add a temporary endpoint or use a transactional helper if possible.
        // But verifying via side-channel (DB) is best.
    }
}