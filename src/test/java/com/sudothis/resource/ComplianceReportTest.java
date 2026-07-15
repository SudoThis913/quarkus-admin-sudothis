package com.sudothis.resource;

import com.sudothis.FixtureService;
import com.sudothis.FixtureService.OrgAdminFixture;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Compliance report endpoints are site-tier only: a fresh admin user is
 * bootstrapped as SITE_ADMIN by ensureActor() and must get reports; a scoped
 * ORG_ADMIN must be rejected with 403.
 */
@QuarkusTest
public class ComplianceReportTest {

    @Inject
    FixtureService fixtures;

    private OrgAdminFixture orgAdminF;

    @AfterEach
    void cleanup() {
        if (orgAdminF != null) { fixtures.cleanupOrgAdmin(orgAdminF); orgAdminF = null; }
    }

    @Test
    @TestSecurity(user = "compliance-site-admin", roles = {"admin"})
    void testSiteAdminSeesRoleMatrix() {
        given()
            .when().get("/api/admin/compliance/roles")
            .then().statusCode(200)
            // the actor's own bootstrapped SITE_ADMIN grant must appear in the matrix
            .body("keycloakSub", hasItem("compliance-site-admin"))
            .body("find { it.keycloakSub == 'compliance-site-admin' }.role", equalTo("SITE_ADMIN"));
    }

    @Test
    @TestSecurity(user = "compliance-site-admin-2", roles = {"admin"})
    void testSiteAdminGetsAuditSummaryAndDeniedAttempts() {
        given()
            .when().get("/api/admin/compliance/audit-summary?days=7")
            .then().statusCode(200)
            .body("$", isA(java.util.List.class));

        given()
            .when().get("/api/admin/compliance/denied-attempts?days=7")
            .then().statusCode(200)
            .body("$", isA(java.util.List.class));
    }

    @Test
    @TestSecurity(user = "compliance-org-admin", roles = {"admin"})
    void testScopedAdminGets403OnComplianceReports() {
        orgAdminF = fixtures.createOrgAdminFixture("compliance-org-admin");

        given().when().get("/api/admin/compliance/roles").then().statusCode(403);
        given().when().get("/api/admin/compliance/audit-summary").then().statusCode(403);
        given().when().get("/api/admin/compliance/denied-attempts").then().statusCode(403);
    }
}
