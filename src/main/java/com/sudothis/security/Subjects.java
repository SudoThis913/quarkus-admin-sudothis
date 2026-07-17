package com.sudothis.security;

import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Single source of truth for the actor identifier.
 *
 * The canonical actor id is the immutable OIDC {@code sub} claim (the Keycloak
 * user UUID). Code-flow (browser) sessions do not expose {@code sub} as a
 * SecurityIdentity attribute — it is only present on the JWT principal — which
 * is why every call site must go through this resolver instead of
 * {@code identity.getAttribute("sub")}.
 *
 * Resolution order:
 *  1. JWT principal's sub claim (OIDC bearer and code-flow sessions)
 *  2. "sub" identity attribute (older token types)
 *  3. principal name — @TestSecurity and %h2 form-auth have no JWT; their
 *     principal name is the stable identifier in those environments
 */
public final class Subjects {

    private Subjects() {}

    public static String of(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous()) {
            return null;
        }
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
        }
        String attr = identity.getAttribute("sub");
        if (attr != null) {
            return attr;
        }
        return identity.getPrincipal().getName();
    }
}
