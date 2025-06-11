package com.sudothis.service;

import com.sudothis.model.*;
import com.sudothis.security.AccessControlService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class SessionService {

    public static final Duration TTL = Duration.ofDays(30);
    private static final String P = "session:";

    @Inject RedisDataSource redis;
    @Inject EntityManager em;
    @Inject AccessControlService acl;

    private void cache(String sid, SecurityIdentity id) {
        redis.value(SecurityIdentity.class).set(P + sid, id);
        redis.key().expire(P + sid, TTL);
    }
    private SecurityIdentity cached(String sid) {
        return redis.value(SecurityIdentity.class).get(P + sid);
    }

    /* ---- create ---- */
    @Transactional
    public String createSession(AppUser u, String ip, String csrf) {
        PersistentSession ps = new PersistentSession();
        ps.ipAddress = ip;
        ps.csrfToken = csrf;
        ps.expiresAt = Instant.now().plus(TTL);
        u.addSession(ps);
        em.persist(ps);
        cache(ps.id, toId(u));
        return ps.id;
    }

    public SecurityIdentity identityFromRedis(String sid) { return cached(sid); }

    @Transactional
    public CompletionStage<SecurityIdentity> identityFromDatabase(String sid) {
        PersistentSession ps = em.find(PersistentSession.class, sid);
        if (ps == null || ps.expired()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.completedFuture(toId(ps.user));
    }

    public void cacheIdentity(String sid, SecurityIdentity id) { cache(sid, id); }

    @Transactional
    public void invalidate(String sid) {
        redis.key().del(P + sid);
        Optional.ofNullable(em.find(PersistentSession.class, sid)).ifPresent(em::remove);
    }

    private SecurityIdentity toId(AppUser u) {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> u.username)
                .addRoles(acl.rolesForUser(u))
                .build();
    }
}