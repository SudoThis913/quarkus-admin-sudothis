package com.sudothis.resource;

import com.sudothis.FixtureService;
import com.sudothis.FixtureService.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that permission boundaries are enforced at query time.
 *
 * Fixture data is committed before each test (so HTTP endpoints see it in separate transactions),
 * then cleaned up in @AfterEach. Tests use @TestSecurity with the actor's keycloakSub as the
 * "user" value — ensureActor() falls back to principalName when no sub claim is present.
 */
@QuarkusTest
public class PermissionIsolationTest {

    @Inject
    FixtureService fixtures;

    // Store cleanup handles across test / afterEach boundary
    private OrgAdminFixture orgAdminF;
    private CompanyAdminFixture companyAdminF;
    private TeamAdminFixture teamAdminF;
    private SiteAgentFixture siteAgentF;
    private ImpersonationFixture impersonationF;

    @AfterEach
    void cleanup() {
        if (orgAdminF != null)     { fixtures.cleanupOrgAdmin(orgAdminF);         orgAdminF = null; }
        if (companyAdminF != null) { fixtures.cleanupCompanyAdmin(companyAdminF); companyAdminF = null; }
        if (teamAdminF != null)    { fixtures.cleanupTeamAdmin(teamAdminF);       teamAdminF = null; }
        if (siteAgentF != null)    { fixtures.cleanupSiteAgent(siteAgentF);       siteAgentF = null; }
        if (impersonationF != null){ fixtures.cleanupImpersonation(impersonationF); impersonationF = null; }
    }

    // ------------------------------------------------------------------
    // ORG_ADMIN isolation
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "iso-org-admin", roles = {"admin"})
    void testOrgAdminListSeesOnlyOwnOrg() {
        orgAdminF = fixtures.createOrgAdminFixture("iso-org-admin");

        // List Companies — should see coA (in orgA) but NOT coB (in orgB)
        var ids = given()
            .when().get("/api/admin/data/Company")
            .then().statusCode(200)
            .extract().jsonPath().getList("id", Integer.class);

        assertTrue(ids.contains(orgAdminF.coA().id.intValue()),
            "ORG_ADMIN should see their own org's company");
        assertFalse(ids.contains(orgAdminF.coB().id.intValue()),
            "ORG_ADMIN must NOT see another org's company");
    }

    @Test
    @TestSecurity(user = "iso-org-admin-get", roles = {"admin"})
    void testOrgAdminCannotGetEntityFromOtherOrg() {
        orgAdminF = fixtures.createOrgAdminFixture("iso-org-admin-get");

        // Direct GET on coB (belongs to orgB) must return 403
        given()
            .when().get("/api/admin/data/Company/" + orgAdminF.coB().id)
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "iso-org-admin-get2", roles = {"admin"})
    void testOrgAdminCanGetOwnOrgEntity() {
        orgAdminF = fixtures.createOrgAdminFixture("iso-org-admin-get2");

        given()
            .when().get("/api/admin/data/Company/" + orgAdminF.coA().id)
            .then().statusCode(200)
            .body("id", equalTo(orgAdminF.coA().id.intValue()));
    }

    @Test
    @TestSecurity(user = "iso-org-admin-put", roles = {"admin"})
    void testOrgAdminCannotUpdateEntityFromOtherOrg() {
        orgAdminF = fixtures.createOrgAdminFixture("iso-org-admin-put");

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"hacked\"}")
            .when().put("/api/admin/data/Company/" + orgAdminF.coB().id)
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "iso-org-admin-del", roles = {"admin"})
    void testOrgAdminCannotDeleteEntityFromOtherOrg() {
        orgAdminF = fixtures.createOrgAdminFixture("iso-org-admin-del");

        given()
            .when().delete("/api/admin/data/Company/" + orgAdminF.coB().id)
            .then().statusCode(403);
    }

    // ------------------------------------------------------------------
    // COMPANY_ADMIN isolation
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "iso-co-admin", roles = {"admin"})
    void testCompanyAdminSeesOnlyOwnCompanyTeams() {
        companyAdminF = fixtures.createCompanyAdminFixture("iso-co-admin");

        var ids = given()
            .when().get("/api/admin/data/Team")
            .then().statusCode(200)
            .extract().jsonPath().getList("id", Integer.class);

        assertTrue(ids.contains(companyAdminF.teamX().id.intValue()),
            "COMPANY_ADMIN should see their own company's team");
        assertFalse(ids.contains(companyAdminF.teamY().id.intValue()),
            "COMPANY_ADMIN must NOT see another company's team");
    }

    @Test
    @TestSecurity(user = "iso-co-admin-get", roles = {"admin"})
    void testCompanyAdminCannotGetOtherCompanyEntity() {
        companyAdminF = fixtures.createCompanyAdminFixture("iso-co-admin-get");

        given()
            .when().get("/api/admin/data/Company/" + companyAdminF.coY().id)
            .then().statusCode(403);
    }

    // ------------------------------------------------------------------
    // TEAM_ADMIN isolation
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "iso-team-admin", roles = {"admin"})
    void testTeamAdminSeesOnlyOwnTeam() {
        teamAdminF = fixtures.createTeamAdminFixture("iso-team-admin");

        var ids = given()
            .when().get("/api/admin/data/Team")
            .then().statusCode(200)
            .extract().jsonPath().getList("id", Integer.class);

        assertTrue(ids.contains(teamAdminF.teamT().id.intValue()),
            "TEAM_ADMIN should see their own team");
        assertFalse(ids.contains(teamAdminF.teamU().id.intValue()),
            "TEAM_ADMIN must NOT see a team they don't manage");
    }

    @Test
    @TestSecurity(user = "iso-team-admin-get", roles = {"admin"})
    void testTeamAdminCannotGetOtherTeam() {
        teamAdminF = fixtures.createTeamAdminFixture("iso-team-admin-get");

        given()
            .when().get("/api/admin/data/Team/" + teamAdminF.teamU().id)
            .then().statusCode(403);
    }

    // ------------------------------------------------------------------
    // SITE_AGENT — cross-tenant access
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "iso-site-agent", roles = {"admin"})
    void testSiteAgentSeesAllCompanies() {
        siteAgentF = fixtures.createSiteAgentFixture("iso-site-agent");

        var ids = given()
            .when().get("/api/admin/data/Company")
            .then().statusCode(200)
            .extract().jsonPath().getList("id", Integer.class);

        assertTrue(ids.contains(siteAgentF.coA().id.intValue()),
            "SITE_AGENT must see all companies (coA)");
        assertTrue(ids.contains(siteAgentF.coB().id.intValue()),
            "SITE_AGENT must see all companies (coB)");
    }

    @Test
    @TestSecurity(user = "iso-site-agent-get", roles = {"admin"})
    void testSiteAgentCanGetAnyEntity() {
        siteAgentF = fixtures.createSiteAgentFixture("iso-site-agent-get");

        given()
            .when().get("/api/admin/data/Company/" + siteAgentF.coB().id)
            .then().statusCode(200);
    }

    // ------------------------------------------------------------------
    // Impersonation — session creation and audit trail
    // ------------------------------------------------------------------

    @Test
    @TestSecurity(user = "iso-site-admin-imp", roles = {"admin"})
    void testSiteAdminCanStartImpersonationSession() {
        impersonationF = fixtures.createImpersonationFixture(
            "iso-site-admin-imp", "iso-target-user-imp");

        String body = String.format(
            "{\"targetUserId\": %d, \"reason\": \"Test impersonation\"}",
            impersonationF.targetUser().id);

        JsonPath response = given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/admin/impersonation")
            .then().statusCode(201)
            .body("sessionToken", not(emptyString()))
            .body("impersonatorSub", equalTo("iso-site-admin-imp"))
            .body("targetSub", equalTo("iso-target-user-imp"))
            .extract().jsonPath();

        // Clean up the session
        long sessionId = response.getLong("id");
        given()
            .when().delete("/api/admin/impersonation/" + sessionId)
            .then().statusCode(204);
    }

    @Test
    @TestSecurity(user = "iso-site-admin-imp2", roles = {"admin"})
    void testCannotImpersonateSiteTierAccount() {
        impersonationF = fixtures.createImpersonationFixture(
            "iso-site-admin-imp2", "iso-target-user-imp2");

        // Try to impersonate the siteAdmin (who is also site-tier) — should be forbidden
        String body = String.format(
            "{\"targetUserId\": %d, \"reason\": \"Should be denied\"}",
            impersonationF.siteAdmin().id);

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/admin/impersonation")
            .then().statusCode(403);
    }
}
