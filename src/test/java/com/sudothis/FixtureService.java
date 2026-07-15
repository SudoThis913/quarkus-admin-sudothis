package com.sudothis;

import com.sudothis.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

/**
 * Test-only ApplicationScoped bean that creates and tears down fixture data in committed
 * transactions. HTTP endpoints run in separate threads/transactions and must see committed
 * data — @TestTransaction cannot be used for that reason.
 */
@ApplicationScoped
public class FixtureService {

    public record OrgAdminFixture(
        Organization orgA, Organization orgB,
        Company coA, Company coB,
        AppUser alice
    ) {}

    public record CompanyAdminFixture(
        Organization org,
        Company coX, Company coY,
        Team teamX, Team teamY,
        AppUser bob
    ) {}

    public record TeamAdminFixture(
        Organization org,
        Company company,
        Team teamT, Team teamU,
        AppUser charlie
    ) {}

    public record SiteAgentFixture(
        Organization orgA, Organization orgB,
        Company coA, Company coB,
        AppUser dan
    ) {}

    public record ImpersonationFixture(
        AppUser siteAdmin,
        AppUser targetUser,
        Organization org,
        Company company
    ) {}

    // ------------------------------------------------------------------
    // ORG_ADMIN fixture: alice has ORG_ADMIN on orgA, not orgB
    // ------------------------------------------------------------------

    @Transactional
    public OrgAdminFixture createOrgAdminFixture(String actorSub) {
        Organization orgA = new Organization();
        orgA.name = "OrgA-" + actorSub + "-" + System.nanoTime();
        orgA.active = true;
        orgA.persist();

        Organization orgB = new Organization();
        orgB.name = "OrgB-" + actorSub + "-" + System.nanoTime();
        orgB.active = true;
        orgB.persist();

        Company coA = new Company();
        coA.name = "CoA-" + System.nanoTime();
        coA.organization = orgA;
        coA.active = true;
        coA.persist();

        Company coB = new Company();
        coB.name = "CoB-" + System.nanoTime();
        coB.organization = orgB;
        coB.active = true;
        coB.persist();

        AppUser alice = AppUser.ensureExists(actorSub, actorSub, actorSub + "@test.com");

        AdminPermission perm = new AdminPermission();
        perm.user = alice;
        perm.role = AdminPermission.Role.ORG_ADMIN;
        perm.scopeType = AdminPermission.ScopeType.ORGANIZATION;
        perm.targetId = orgA.id;
        perm.grantedBy = "test-setup";
        perm.grantedAt = Instant.now();
        perm.persist();

        return new OrgAdminFixture(orgA, orgB, coA, coB, alice);
    }

    @Transactional
    public void cleanupOrgAdmin(OrgAdminFixture f) {
        AdminPermission.delete("user = ?1", f.alice());
        f.coA().delete();
        f.coB().delete();
        f.orgA().delete();
        f.orgB().delete();
        f.alice().delete();
    }

    // ------------------------------------------------------------------
    // COMPANY_ADMIN fixture: bob has COMPANY_ADMIN on coX, not coY
    // ------------------------------------------------------------------

    @Transactional
    public CompanyAdminFixture createCompanyAdminFixture(String actorSub) {
        Organization org = new Organization();
        org.name = "Org-" + actorSub + "-" + System.nanoTime();
        org.active = true;
        org.persist();

        Company coX = new Company();
        coX.name = "CoX-" + System.nanoTime();
        coX.organization = org;
        coX.active = true;
        coX.persist();

        Company coY = new Company();
        coY.name = "CoY-" + System.nanoTime();
        coY.organization = org;
        coY.active = true;
        coY.persist();

        Team teamX = new Team();
        teamX.name = "TeamX-" + System.nanoTime();
        teamX.company = coX;
        teamX.persist();

        Team teamY = new Team();
        teamY.name = "TeamY-" + System.nanoTime();
        teamY.company = coY;
        teamY.persist();

        AppUser bob = AppUser.ensureExists(actorSub, actorSub, actorSub + "@test.com");

        AdminPermission perm = new AdminPermission();
        perm.user = bob;
        perm.role = AdminPermission.Role.COMPANY_ADMIN;
        perm.scopeType = AdminPermission.ScopeType.COMPANY;
        perm.targetId = coX.id;
        perm.grantedBy = "test-setup";
        perm.grantedAt = Instant.now();
        perm.persist();

        return new CompanyAdminFixture(org, coX, coY, teamX, teamY, bob);
    }

    @Transactional
    public void cleanupCompanyAdmin(CompanyAdminFixture f) {
        AdminPermission.delete("user = ?1", f.bob());
        f.teamX().delete();
        f.teamY().delete();
        f.coX().delete();
        f.coY().delete();
        f.org().delete();
        f.bob().delete();
    }

    // ------------------------------------------------------------------
    // TEAM_ADMIN fixture: charlie has TEAM_ADMIN on teamT, not teamU
    // ------------------------------------------------------------------

    @Transactional
    public TeamAdminFixture createTeamAdminFixture(String actorSub) {
        Organization org = new Organization();
        org.name = "Org-" + actorSub + "-" + System.nanoTime();
        org.active = true;
        org.persist();

        Company company = new Company();
        company.name = "Co-" + System.nanoTime();
        company.organization = org;
        company.active = true;
        company.persist();

        Team teamT = new Team();
        teamT.name = "TeamT-" + System.nanoTime();
        teamT.company = company;
        teamT.persist();

        Team teamU = new Team();
        teamU.name = "TeamU-" + System.nanoTime();
        teamU.company = company;
        teamU.persist();

        AppUser charlie = AppUser.ensureExists(actorSub, actorSub, actorSub + "@test.com");

        AdminPermission perm = new AdminPermission();
        perm.user = charlie;
        perm.role = AdminPermission.Role.TEAM_ADMIN;
        perm.scopeType = AdminPermission.ScopeType.TEAM;
        perm.targetId = teamT.id;
        perm.grantedBy = "test-setup";
        perm.grantedAt = Instant.now();
        perm.persist();

        return new TeamAdminFixture(org, company, teamT, teamU, charlie);
    }

    @Transactional
    public void cleanupTeamAdmin(TeamAdminFixture f) {
        AdminPermission.delete("user = ?1", f.charlie());
        f.teamT().delete();
        f.teamU().delete();
        f.company().delete();
        f.org().delete();
        f.charlie().delete();
    }

    // ------------------------------------------------------------------
    // SITE_AGENT fixture: dan sees everything
    // ------------------------------------------------------------------

    @Transactional
    public SiteAgentFixture createSiteAgentFixture(String actorSub) {
        Organization orgA = new Organization();
        orgA.name = "SiteA-" + System.nanoTime();
        orgA.active = true;
        orgA.persist();

        Organization orgB = new Organization();
        orgB.name = "SiteB-" + System.nanoTime();
        orgB.active = true;
        orgB.persist();

        Company coA = new Company();
        coA.name = "SiteCoA-" + System.nanoTime();
        coA.organization = orgA;
        coA.active = true;
        coA.persist();

        Company coB = new Company();
        coB.name = "SiteCoB-" + System.nanoTime();
        coB.organization = orgB;
        coB.active = true;
        coB.persist();

        AppUser dan = AppUser.ensureExists(actorSub, actorSub, actorSub + "@test.com");

        AdminPermission perm = new AdminPermission();
        perm.user = dan;
        perm.role = AdminPermission.Role.SITE_AGENT;
        perm.scopeType = AdminPermission.ScopeType.SITE;
        perm.targetId = null;
        perm.grantedBy = "test-setup";
        perm.grantedAt = Instant.now();
        perm.persist();

        return new SiteAgentFixture(orgA, orgB, coA, coB, dan);
    }

    @Transactional
    public void cleanupSiteAgent(SiteAgentFixture f) {
        AdminPermission.delete("user = ?1", f.dan());
        f.coA().delete();
        f.coB().delete();
        f.orgA().delete();
        f.orgB().delete();
        f.dan().delete();
    }

    // ------------------------------------------------------------------
    // Impersonation fixture: siteAdmin impersonating a non-site user
    // ------------------------------------------------------------------

    @Transactional
    public ImpersonationFixture createImpersonationFixture(String siteAdminSub, String targetSub) {
        Organization org = new Organization();
        org.name = "ImpOrg-" + System.nanoTime();
        org.active = true;
        org.persist();

        Company company = new Company();
        company.name = "ImpCo-" + System.nanoTime();
        company.organization = org;
        company.active = true;
        company.persist();

        AppUser siteAdmin = AppUser.ensureExists(siteAdminSub, siteAdminSub, siteAdminSub + "@test.com");
        AdminPermission sitePerm = new AdminPermission();
        sitePerm.user = siteAdmin;
        sitePerm.role = AdminPermission.Role.SITE_ADMIN;
        sitePerm.scopeType = AdminPermission.ScopeType.SITE;
        sitePerm.grantedBy = "test-setup";
        sitePerm.grantedAt = Instant.now();
        sitePerm.persist();

        AppUser targetUser = AppUser.ensureExists(targetSub, targetSub, targetSub + "@test.com");
        AdminPermission targetPerm = new AdminPermission();
        targetPerm.user = targetUser;
        targetPerm.role = AdminPermission.Role.ORG_ADMIN;
        targetPerm.scopeType = AdminPermission.ScopeType.ORGANIZATION;
        targetPerm.targetId = org.id;
        targetPerm.grantedBy = "test-setup";
        targetPerm.grantedAt = Instant.now();
        targetPerm.persist();

        return new ImpersonationFixture(siteAdmin, targetUser, org, company);
    }

    @Transactional
    public void cleanupImpersonation(ImpersonationFixture f) {
        ImpersonationSession.delete("impersonator = ?1 or target = ?1", f.siteAdmin());
        AdminPermission.delete("user = ?1 or user = ?2", f.siteAdmin(), f.targetUser());
        f.company().delete();
        f.org().delete();
        f.siteAdmin().delete();
        f.targetUser().delete();
    }
}
