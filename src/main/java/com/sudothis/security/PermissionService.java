package com.sudothis.security;

import com.sudothis.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class PermissionService {

    private static final Logger LOG = Logger.getLogger(PermissionService.class);

    @Inject
    EntityManager em;

    @Inject
    ImpersonationContext impersonationCtx;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Resolves the union of all permissions for a user from:
     *   1. Direct AdminPermission records (not expired)
     *   2. Active GroupMembership → PermissionGroup
     *   3. UserType → PermissionGroup
     */
    public List<EffectivePermission> getEffectivePermissions(AppUser user) {
        Set<EffectivePermission> perms = new LinkedHashSet<>();

        // 1. Direct AdminPermission records
        List<AdminPermission> direct = AdminPermission.list(
            "user = ?1 and (expiresAt is null or expiresAt > ?2)",
            user, Instant.now()
        );
        for (AdminPermission p : direct) {
            perms.add(new EffectivePermission(p.role, p.scopeType, p.targetId));
        }

        // 2. Group memberships
        List<GroupMembership> memberships = GroupMembership.list(
            "user = ?1 and active = true and (expiresAt is null or expiresAt > ?2)",
            user, Instant.now()
        );
        for (GroupMembership gm : memberships) {
            PermissionGroup g = gm.group;
            perms.add(new EffectivePermission(g.defaultRole, g.scopeType, g.targetId));
        }

        // 3. UserType groups
        if (user.userType != null) {
            UserType ut = UserType.findById(user.userType.id);
            if (ut != null && ut.active) {
                for (PermissionGroup g : ut.permissionGroups) {
                    if (g.active) {
                        perms.add(new EffectivePermission(g.defaultRole, g.scopeType, g.targetId));
                    }
                }
            }
        }

        return new ArrayList<>(perms);
    }

    /**
     * Returns true if the actor (identified by keycloakSub) may access/modify
     * the given entity. For list operations pass entityId=null; for single-entity
     * operations pass the entity's actual ID so ownership can be verified.
     *
     * If an impersonation session is active, the impersonator's permissions are used
     * (impersonation does not elevate to target's permissions).
     */
    public boolean canManage(String actorKeycloakSub, Class<?> entityClass, Long entityId) {
        AppUser actor = resolveActor(actorKeycloakSub);
        if (actor == null) return false;

        // Impersonation: use impersonator's permissions, not the target's
        if (impersonationCtx.isActive()) {
            actor = impersonationCtx.getImpersonator();
        }

        List<EffectivePermission> perms = getEffectivePermissions(actor);

        // Site-tier → access to all data (management rules checked separately)
        if (hasSiteTierPermission(perms)) return true;

        // Scoped roles require the entity to implement TenantAware
        if (entityId != null && TenantAware.class.isAssignableFrom(entityClass)) {
            Object entity = em.find(entityClass, entityId);
            if (entity instanceof TenantAware tenantAware) {
                return hasAccessToEntity(perms, tenantAware);
            }
        }

        // For list operations (entityId=null) filtering is handled by getFilterClauses()
        return entityId == null && hasTenantPermission(perms);
    }

    /**
     * Returns JPQL WHERE clauses for tenant isolation on list queries.
     * The entityAlias is the JPQL alias used for the entity (e.g., "e").
     *
     * Site-tier roles: no filter (see all).
     * Scoped roles: clauses restrict to managed orgs/companies/teams.
     * Non-TenantAware entity + non-site role: returns FilterResult.deny().
     */
    public FilterResult getFilterClauses(AppUser actor, Class<?> entityClass, String entityAlias) {
        List<EffectivePermission> perms = getEffectivePermissions(actor);

        if (hasSiteTierPermission(perms)) {
            return FilterResult.allow();
        }

        if (!TenantAware.class.isAssignableFrom(entityClass)) {
            // Non-TenantAware entities are not accessible to non-site roles
            return FilterResult.deny();
        }

        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        // Resolve JPQL path expressions via TenantAware self-description
        TenantAware prototype = null;
        try {
            prototype = (TenantAware) entityClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            LOG.warnf("Cannot instantiate %s to resolve JPQL paths; denying", entityClass.getSimpleName());
        }
        if (prototype == null) return FilterResult.deny();

        List<Long> orgIds = collectTargetIds(perms, AdminPermission.ScopeType.ORGANIZATION);
        List<Long> companyIds = collectTargetIds(perms, AdminPermission.ScopeType.COMPANY);
        List<Long> teamIds = collectTargetIds(perms, AdminPermission.ScopeType.TEAM);

        String orgPath     = prototype.jpqlOrganizationIdPath();
        String companyPath = prototype.jpqlCompanyIdPath();
        String teamPath    = prototype.jpqlTeamIdPath();

        if (!orgIds.isEmpty() && orgPath != null) {
            clauses.add(entityAlias + "." + orgPath + " IN (:permOrgIds)");
            params.put("permOrgIds", orgIds);
        }
        if (!companyIds.isEmpty() && companyPath != null) {
            clauses.add(entityAlias + "." + companyPath + " IN (:permCompanyIds)");
            params.put("permCompanyIds", companyIds);
        }
        if (!teamIds.isEmpty() && teamPath != null) {
            clauses.add(entityAlias + "." + teamPath + " IN (:permTeamIds)");
            params.put("permTeamIds", teamIds);
        }

        if (clauses.isEmpty()) {
            // User has scoped role enum but no assigned targets yet
            return FilterResult.deny();
        }

        // Multiple scope levels are OR-ed together (a user with both ORG and COMPANY perms
        // should see entities from either scope)
        String combined = "(" + String.join(" OR ", clauses) + ")";
        return FilterResult.withClauses(List.of(combined), params);
    }

    /**
     * Returns true if actorSub may manage (assign/revoke permissions on) targetUser.
     *
     * SITE_OWNER   → can manage any account
     * SITE_ADMIN   → can manage SITE_AGENT and below; cannot manage SITE_ADMIN/SITE_OWNER
     * SITE_AGENT   → can manage ORG_ADMIN and below; cannot manage any site-tier account
     */
    public boolean canManagePrincipal(String actorKeycloakSub, AppUser targetUser) {
        AppUser actor = resolveActor(actorKeycloakSub);
        if (actor == null || targetUser == null) return false;

        List<EffectivePermission> actorPerms = getEffectivePermissions(actor);
        AdminPermission.Role actorHighest = highestRole(actorPerms);
        AdminPermission.Role targetHighest = highestRole(getEffectivePermissions(targetUser));

        return switch (actorHighest) {
            case SITE_OWNER -> true;
            case SITE_ADMIN -> targetHighest != AdminPermission.Role.SITE_OWNER
                            && targetHighest != AdminPermission.Role.SITE_ADMIN;
            case SITE_AGENT -> !AdminPermission.isSiteTierRole(targetHighest);
            default -> false;
        };
    }

    /**
     * Validates that an employee user is not being associated with more than one company.
     * Must be called before persisting or updating an AppUser.
     */
    public void validateEmployeeConstraints(AppUser user) {
        if (user.isEmployee && user.companies.size() > 1) {
            throw new IllegalArgumentException(
                "Employee users cannot belong to more than one company.");
        }
    }

    /**
     * Adds a user to a PermissionGroup, recording the granting admin's sub.
     * No-op if the user is already an active member.
     */
    @Transactional
    public void ensureGroupMembership(AppUser user, PermissionGroup group, String grantedBySub) {
        long existing = GroupMembership.count(
            "user = ?1 and group = ?2 and active = true", user, group);
        if (existing > 0) return;

        GroupMembership gm = new GroupMembership();
        gm.user = user;
        gm.group = group;
        gm.grantedBy = grantedBySub;
        gm.grantedAt = Instant.now();
        gm.persist();
        LOG.infof("Added user %s to group %s (granted by %s)", user.keycloakSub, group.name, grantedBySub);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    public AppUser resolveActor(String keycloakSub) {
        if (keycloakSub == null) return null;
        return AppUser.find("keycloakSub", keycloakSub).firstResult();
    }

    /**
     * Resolves or bootstraps an actor from the SecurityIdentity.
     * Uses the `sub` claim when available, falling back to the principal name
     * (for @TestSecurity compatibility). If the user doesn't exist locally and
     * has the "admin" Keycloak role, they are auto-provisioned as SITE_ADMIN.
     */
    @Transactional
    public AppUser ensureActor(io.quarkus.security.identity.SecurityIdentity identity) {
        String sub = identity.getAttribute("sub");
        String principalName = identity.getPrincipal().getName();
        String effectiveSub = (sub != null) ? sub : principalName;

        AppUser actor = AppUser.find("keycloakSub", effectiveSub).firstResult();
        if (actor != null) return actor;

        // Bootstrap: first-time admin login — provision user + SITE_ADMIN permission
        actor = AppUser.ensureExists(effectiveSub, principalName, identity.getAttribute("email"));

        if (identity.hasRole("admin")) {
            AdminPermission p = new AdminPermission();
            p.user = actor;
            p.role = AdminPermission.Role.SITE_ADMIN;
            p.scopeType = AdminPermission.ScopeType.SITE;
            p.grantedBy = "bootstrap";
            p.grantedAt = java.time.Instant.now();
            p.persist();
            LOG.infof("Bootstrapped SITE_ADMIN permission for %s", effectiveSub);
        }

        return actor;
    }

    private boolean hasSiteTierPermission(List<EffectivePermission> perms) {
        return perms.stream().anyMatch(p -> AdminPermission.isSiteTierRole(p.role()));
    }

    private boolean hasTenantPermission(List<EffectivePermission> perms) {
        return perms.stream().anyMatch(p ->
            p.role() == AdminPermission.Role.ORG_ADMIN ||
            p.role() == AdminPermission.Role.COMPANY_ADMIN ||
            p.role() == AdminPermission.Role.TEAM_ADMIN
        );
    }

    private boolean hasAccessToEntity(List<EffectivePermission> perms, TenantAware entity) {
        for (EffectivePermission p : perms) {
            boolean matches = switch (p.scopeType()) {
                case ORGANIZATION -> Objects.equals(p.targetId(), entity.getOrganizationId());
                case COMPANY      -> Objects.equals(p.targetId(), entity.getCompanyId());
                case TEAM         -> Objects.equals(p.targetId(), entity.getTeamId());
                case SITE         -> true;
            };
            if (matches) return true;
        }
        return false;
    }

    private List<Long> collectTargetIds(List<EffectivePermission> perms,
                                         AdminPermission.ScopeType scopeType) {
        return perms.stream()
            .filter(p -> p.scopeType() == scopeType && p.targetId() != null)
            .map(EffectivePermission::targetId)
            .distinct()
            .collect(Collectors.toList());
    }

    private AdminPermission.Role highestRole(List<EffectivePermission> perms) {
        // Ordinal order in enum = ascending privilege, so we want the minimum ordinal
        return perms.stream()
            .map(EffectivePermission::role)
            .min(Comparator.comparingInt(Enum::ordinal))
            .orElse(AdminPermission.Role.USER);
    }
}
