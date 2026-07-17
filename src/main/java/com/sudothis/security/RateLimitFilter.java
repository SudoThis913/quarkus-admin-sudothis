package com.sudothis.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Fixed-window rate limiter for /api/admin/**.
 *
 * Counters are in-process (single-node deployment); the window is one minute.
 * Per-user limit keys on the authenticated sub claim (falling back to principal
 * name, then source IP for anonymous requests). A separate global counter caps
 * total admin-API throughput. Exceeding either limit returns 429 and logs at
 * WARN so the [RATE-LIMIT] marker can feed the same alerting pipeline as
 * [ACCESS-DENIED] events.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 2)
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);
    private static final String ADMIN_API_PREFIX = "/api/admin";
    private static final String GLOBAL_KEY = "__global__";

    @ConfigProperty(name = "sudothis.ratelimit.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "sudothis.ratelimit.per-user-per-minute", defaultValue = "50")
    int perUserLimit;

    @ConfigProperty(name = "sudothis.ratelimit.global-per-minute", defaultValue = "500")
    int globalLimit;

    @Inject
    Instance<SecurityIdentity> identityInstance;

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private volatile long windowEpochMinute = -1;

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (!enabled) return;
        String path = ctx.getUriInfo().getRequestUri().getPath();
        if (!path.startsWith(ADMIN_API_PREFIX)) return;

        rollWindowIfNeeded();

        String actor = resolveActorKey(ctx);
        int userCount = counters.computeIfAbsent(actor, k -> new AtomicInteger()).incrementAndGet();
        int globalCount = counters.computeIfAbsent(GLOBAL_KEY, k -> new AtomicInteger()).incrementAndGet();

        if (userCount > perUserLimit || globalCount > globalLimit) {
            String scope = userCount > perUserLimit ? "user" : "global";
            LOG.warnf("[RATE-LIMIT] actor=%s path=%s scope=%s userCount=%d globalCount=%d",
                actor, path, scope, userCount, globalCount);
            ctx.abortWith(Response.status(429)
                .header("Retry-After", "60")
                .entity(Map.of("error", "Rate limit exceeded. Retry after 60 seconds."))
                .build());
        }
    }

    private void rollWindowIfNeeded() {
        long nowMinute = System.currentTimeMillis() / 60_000L;
        if (nowMinute != windowEpochMinute) {
            synchronized (this) {
                if (nowMinute != windowEpochMinute) {
                    counters.clear();
                    windowEpochMinute = nowMinute;
                }
            }
        }
    }

    private String resolveActorKey(ContainerRequestContext ctx) {
        try {
            String sub = Subjects.of(identityInstance.get());
            if (sub != null) {
                return sub;
            }
        } catch (Exception ignored) {
            // fall through to IP-based key
        }
        String ip = ctx.getHeaderString("X-Forwarded-For");
        return ip != null ? "ip:" + ip : "ip:unknown";
    }
}
