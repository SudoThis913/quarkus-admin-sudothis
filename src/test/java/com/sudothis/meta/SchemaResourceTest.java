package com.sudothis.meta;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

@QuarkusTest
public class SchemaResourceTest {

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testSchemaDiscovery() {
        given()
            .when().get("/api/admin/schema")
            .then()
            .statusCode(200)
            .body("name", hasItem("AppUser"))
            .body("name", hasItem("Organization"))
            .body("name", hasItem("Team"))
            .body("find { it.name == 'AppUser' }.displayName", is("Users"));
    }
}
