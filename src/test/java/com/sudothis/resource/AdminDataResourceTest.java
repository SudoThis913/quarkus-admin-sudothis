package com.sudothis.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudothis.meta.ModelRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
public class AdminDataResourceTest {

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testListUsers() {
        given()
            .when()
            .get("/api/admin/data/AppUser")
        .then()
            .statusCode(200)
            .body("size()", Matchers.greaterThanOrEqualTo(0)); // Might be empty if no seed
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testCRUDOrganization() {
        // 1. Create
        String orgName = "Acme Corp " + System.currentTimeMillis();
        String json = "{\"name\": \"" + orgName + "\", \"active\": true}";

        int id = given()
            .contentType(ContentType.JSON)
            .body(json)
        .when()
            .post("/api/admin/data/Organization")
        .then()
            .statusCode(201)
            .extract().path("id");

        // 2. Get
        given()
            .when()
            .get("/api/admin/data/Organization/" + id)
        .then()
            .statusCode(200)
            .body("name", Matchers.equalTo(orgName));

        // 3. Update
        String updatedName = orgName + " Updated";
        String updateJson = "{\"name\": \"" + updatedName + "\"}";
        
        given()
            .contentType(ContentType.JSON)
            .body(updateJson)
        .when()
            .put("/api/admin/data/Organization/" + id)
        .then()
            .statusCode(200)
            .body("name", Matchers.equalTo(updatedName));

        // 4. List with Sort
        given()
            .queryParam("sort", "-name")
            .when()
            .get("/api/admin/data/Organization")
        .then()
            .statusCode(200);

        // 5. Delete
        given()
            .when()
            .delete("/api/admin/data/Organization/" + id)
        .then()
            .statusCode(204);

        // 6. Verify Gone
        given()
            .when()
            .get("/api/admin/data/Organization/" + id)
        .then()
            .statusCode(404);
            
        // 7. Test Filtering
        String orgName2 = "Another Org " + System.currentTimeMillis();
        String json2 = "{\"name\": \"" + orgName2 + "\", \"active\": true}";
        given().contentType(ContentType.JSON).body(json2).when().post("/api/admin/data/Organization").then().statusCode(201);
        
        String orgName3 = "Something Else " + System.currentTimeMillis();
        String json3 = "{\"name\": \"" + orgName3 + "\", \"active\": true}";
        given().contentType(ContentType.JSON).body(json3).when().post("/api/admin/data/Organization").then().statusCode(201);
        
        given()
            .queryParam("name", "Another Org")
            .when()
            .get("/api/admin/data/Organization")
        .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].name", Matchers.containsString("Another Org"));
    }

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    public void testCreateUserWithOrgId() {
        // 1. Create Org
        String orgName = "RelOrg " + System.currentTimeMillis();
        int orgId = given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"" + orgName + "\", \"active\": true}")
        .when()
            .post("/api/admin/data/Organization")
        .then()
            .statusCode(201)
            .extract().path("id");

        // 2. Create User with Org ID (Scalar)
        String userName = "user_rel_" + System.currentTimeMillis();
        String userJson = String.format(
            "{\"username\": \"%s\", \"email\": \"%s@test.com\", \"organization\": %d}", 
            userName, userName, orgId
        );

        given()
            .contentType(ContentType.JSON)
            .body(userJson)
        .when()
            .post("/api/admin/data/AppUser")
        .then()
            .statusCode(201)
            .body("organization.id", Matchers.equalTo(orgId));
    }
}
