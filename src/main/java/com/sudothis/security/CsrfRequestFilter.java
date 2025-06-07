// File: src/main/java/com/sudothis/security/CsrfRequestFilter.java

package com.sudothis.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;

import java.io.IOException;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class CsrfRequestFilter implements ContainerRequestFilter {

    @Inject
    RedisDataSource redisDataSource;

    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/auth/login",
        "/auth/whoami",
        "/health",
        "/q/metrics"
    );

    private ValueCommands<String, String> redis;

    @jakarta.annotation.PostConstruct
    void init() {
        this.redis = redisDataSource.value(String.class, String.class);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        if (requestContext.getMethod().equals("GET") || EXCLUDED_PATHS.contains("/" + path)) {
            return;
        }

        Cookie sessionCookie = requestContext.getCookies().get("sudothis_session");
        Cookie csrfCookie = requestContext.getCookies().get("csrf_token");
        String csrfHeader = requestContext.getHeaderString("X-CSRF-Token");

        if (sessionCookie == null || csrfCookie == null || csrfHeader == null) {
            abort(requestContext);
            return;
        }

        String storedToken = redis.get("csrf:" + sessionCookie.getValue());
        if (storedToken == null || !storedToken.equals(csrfHeader)) {
            abort(requestContext);
        }
    }

    private void abort(ContainerRequestContext ctx) {
        ctx.abortWith(Response.status(Response.Status.FORBIDDEN).entity("CSRF validation failed").build());
    }
}

/*
 * === Usage Notes ===
 *
 * This class is automatically registered by the JAX-RS provider system (@Provider).
 * It runs at the AUTHENTICATION priority level and applies to all incoming REST requests.
 *
 * CSRF protection logic:
 * - Skips GET requests and paths defined in EXCLUDED_PATHS (e.g., /auth/login).
 * - For all other requests, expects:
 *     - 'sudothis_session' cookie: identifies user session.
 *     - 'csrf_token' cookie: supplied at login/refresh, accessible via JavaScript.
 *     - 'X-CSRF-Token' header: must match csrf_token value.
 * - Tokens are validated against Redis via key: csrf:{sessionId}
 *
 * To exempt additional paths, add them to EXCLUDED_PATHS.
 * No annotations are needed on controller methods—this filter runs globally.
 */
