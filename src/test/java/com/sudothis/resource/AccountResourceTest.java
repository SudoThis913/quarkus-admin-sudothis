package com.sudothis.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Self-service account management. The %test profile has no Keycloak admin
 * access configured, so profile edits apply locally and password change
 * reports itself unavailable — the authorization rules (own-account only,
 * employee deletion block) are what these tests pin down.
 */
@QuarkusTest
public class AccountResourceTest {

    @Test
    @TestSecurity(user = "acct-page-user", roles = {"user"})
    void testAccountPageRenders() {
        given()
            .when().get("/account")
            .then().statusCode(200)
            .body(containsString("Account settings"))
            .body(containsString("acct-page-user"));
    }

    @Test
    @TestSecurity(user = "acct-profile-user", roles = {"user"})
    void testProfileUpdatePersistsEmail() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("firstName", "Pat")
            .formParam("lastName", "Example")
            .formParam("email", "pat@example.com")
            .redirects().follow(false)
            .when().post("/account/profile")
            .then().statusCode(303)
            .header("Location", containsString("msg="));

        // The account page re-renders the stored email
        given()
            .when().get("/account")
            .then().statusCode(200)
            .body(containsString("pat@example.com"));
    }

    @Test
    @TestSecurity(user = "acct-bad-email", roles = {"user"})
    void testProfileUpdateRejectsInvalidEmail() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("firstName", "Pat")
            .formParam("lastName", "Example")
            .formParam("email", "not-an-email")
            .redirects().follow(false)
            .when().post("/account/profile")
            .then().statusCode(303)
            .header("Location", containsString("err="));
    }

    @Test
    @TestSecurity(user = "acct-pw-user", roles = {"user"})
    void testPasswordChangeUnavailableWithoutKeycloak() {
        given()
            .contentType(ContentType.URLENC)
            .formParam("currentPassword", "old-password-123")
            .formParam("newPassword", "new-password-12345")
            .formParam("confirmPassword", "new-password-12345")
            .redirects().follow(false)
            .when().post("/account/password")
            .then().statusCode(303)
            .header("Location", containsString("err="));
    }

    @Test
    @TestSecurity(user = "acct-employee", roles = {"admin"})
    void testEmployeeCannotDeleteOwnAccount() {
        // First touch must be the admin API: ensureActor() bootstraps SITE_ADMIN
        // only for users it hasn't seen before (visiting /account first would
        // create a permissionless user and the admin calls below would 403)
        Integer id = given()
            .when().get("/api/admin/data/AppUser?keycloakSub=acct-employee")
            .then().statusCode(200)
            .extract().jsonPath().getInt("[0].id");
        given()
            .contentType(ContentType.JSON)
            .body("{\"isEmployee\": true}")
            .when().put("/api/admin/data/AppUser/" + id)
            .then().statusCode(200);

        given()
            .contentType(ContentType.URLENC)
            .formParam("confirm", "DELETE")
            .redirects().follow(false)
            .when().post("/account/delete")
            .then().statusCode(403)
            .body(containsString("administrator"));
    }

    @Test
    @TestSecurity(user = "acct-customer", roles = {"user"})
    void testCustomerCanDeleteOwnAccount() {
        given().when().get("/account").then().statusCode(200);

        // Wrong confirmation text bounces back
        given()
            .contentType(ContentType.URLENC)
            .formParam("confirm", "nope")
            .redirects().follow(false)
            .when().post("/account/delete")
            .then().statusCode(303)
            .header("Location", containsString("err="));

        // Correct confirmation deletes and sends the browser to /logout
        given()
            .contentType(ContentType.URLENC)
            .formParam("confirm", "DELETE")
            .redirects().follow(false)
            .when().post("/account/delete")
            .then().statusCode(303)
            .header("Location", endsWith("/logout"));
    }
}
