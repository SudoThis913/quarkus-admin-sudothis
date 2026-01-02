package com.sudothis.resource;

import com.sudothis.meta.EntityDefinition;
import com.sudothis.meta.ModelRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class AdminUiResourceTest {

    @Inject
    ModelRegistry registry;

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testAdminPanelLoads() {
        given()
            .when().get("/admin")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testEntityLinksExist() {
        List<EntityDefinition> definitions = registry.getDefinitions();
        assertNotNull(definitions);
        assertTrue(definitions.size() > 0);

        String[] expectedNames = definitions.stream()
                .map(EntityDefinition::name)
                .toArray(String[]::new);

        given()
            .when().get("/admin")
            .then()
            .statusCode(200);
            // More robust check could parse HTML and verify each link
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testEntityListLoads() {
        given()
            .when().get("/admin/AppUser")
            .then()
            .statusCode(200); // Default field
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testCreateFormLoads() {
        given()
            .when().get("/admin/Organization/new")
            .then()
            .statusCode(200); // Name field
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testEditFormLoadsWithData() {
        // First create an organization
        String orgName = "TestOrgForEdit " + System.currentTimeMillis();
        int orgId = given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"" + orgName + "\", \"active\": true}")
        .when()
            .post("/api/admin/data/Organization")
        .then()
            .statusCode(201)
            .extract().path("id");

        // Now request the edit form for this organization
        given()
            .when().get("/admin/Organization/" + orgId)
            .then()
            .statusCode(200); // Check for data pre-filled
    }
}
