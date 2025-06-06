package com.sudothis.auth;

import com.sudothis.model.User;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;

import java.time.Duration;

@ApplicationScoped
public class AccessControl {

    private static final Logger LOG = Logger.getLogger(AccessControl.class);
    private static final Duration CACHE_TTL = Duration.ofDays(3);

    @Inject
    EntityManager em;

    @Inject
    RedisDataSource redisDataSource;

    private ValueCommands<String, String> valueCommands;

    @PostConstruct
    void init() {
        this.valueCommands = redisDataSource.value(String.class);
    }

    @Transactional
    public boolean hasPermission(User actingUser, User targetUser, PermissionType type) {
        if (actingUser == null || targetUser == null) return false;

        int actingId = actingUser.getId();
        int targetOrgId = targetUser.getOrgID();
        int targetTeamId = targetUser.getTeamID();

        boolean sameUser = actingId == targetUser.getId();
        if (sameUser && type.allowsSelf()) {
            LOG.debugf("Permission GRANTED via self: %s -> %s", actingUser.getUsername(), targetUser.getUsername());
            return true;
        }

        if (checkCachedPermission("acl:site_admin:" + actingId, () ->
                em.createQuery("SELECT sa.enabled FROM SiteAdmin sa WHERE sa.user.id = :uid", Boolean.class)
                        .setParameter("uid", actingId)
                        .getResultStream().findFirst().orElse(false))) {
            LOG.debugf("Permission GRANTED via site_admin: %s", actingUser.getUsername());
            return true;
        }

        if (type.allowsOrg() && checkCachedPermission("acl:org_admin:" + actingId + ":" + targetOrgId, () ->
                em.createQuery("SELECT oa.enabled FROM OrgAdmin oa WHERE oa.user.id = :uid AND oa.org.id = :orgId", Boolean.class)
                        .setParameter("uid", actingId)
                        .setParameter("orgId", targetOrgId)
                        .getResultStream().findFirst().orElse(false))) {
            LOG.debugf("Permission GRANTED via org_admin: %s on org %d", actingUser.getUsername(), targetOrgId);
            return true;
        }

        if (type.allowsTeam() && checkCachedPermission("acl:team_admin:" + actingId + ":" + targetTeamId, () ->
                em.createQuery("SELECT ta.enabled FROM TeamAdmin ta WHERE ta.user.id = :uid AND ta.team.id = :teamId", Boolean.class)
                        .setParameter("uid", actingId)
                        .setParameter("teamId", targetTeamId)
                        .getResultStream().findFirst().orElse(false))) {
            LOG.debugf("Permission GRANTED via team_admin: %s on team %d", actingUser.getUsername(), targetTeamId);
            return true;
        }

        LOG.debugf("Permission DENIED: %s -> %s for %s", actingUser.getUsername(), targetUser.getUsername(), type);
        return false;
    }

    private boolean checkCachedPermission(String key, PermissionResolver resolver) {
        String cached = valueCommands.get(key);
        if (cached != null && !cached.isEmpty()) {
            return Boolean.parseBoolean(cached);
        }

        boolean result = resolver.resolve();
        valueCommands.setex(key, CACHE_TTL.getSeconds(), String.valueOf(result));
        return result;
    }

    public void clearPermissionCacheForUser(int userId) {
        // Optional: implement using Redis key scanning if needed.
    }

    @FunctionalInterface
    interface PermissionResolver {
        boolean resolve();
    }

    public enum PermissionType {
        UPDATE_EMAIL(true, true, false),
        DELETE_USER(false, true, true),
        RESET_PASSWORD(true, true, true);

        private final boolean allowSelf;
        private final boolean allowOrg;
        private final boolean allowTeam;

        PermissionType(boolean allowSelf, boolean allowOrg, boolean allowTeam) {
            this.allowSelf = allowSelf;
            this.allowOrg = allowOrg;
            this.allowTeam = allowTeam;
        }

        public boolean allowsSelf() {
            return allowSelf;
        }

        public boolean allowsOrg() {
            return allowOrg;
        }

        public boolean allowsTeam() {
            return allowTeam;
        }
    }
}
