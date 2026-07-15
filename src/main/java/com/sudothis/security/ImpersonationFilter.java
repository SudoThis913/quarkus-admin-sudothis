package com.sudothis.security;

import com.sudothis.model.AdminPermission;
import com.sudothis.model.ImpersonationSession;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Provider
@PreMatching
@Priority(Priorities.AUTHORIZATION - 10)
public class ImpersonationFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-Impersonation-Session";

    private static final Logger LOG = Logger.getLogger(ImpersonationFilter.class);

    @Inject
    ImpersonationContext ctx;

    @Inject
    Instance<SecurityIdentity> identityInstance;

    @Inject
    PermissionService permissionService;

    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        String token = req.getHeaderString(HEADER);
        if (token == null) return;

        ImpersonationSession session = ImpersonationSession.<ImpersonationSession>find(
            "sessionToken = ?1 and active = true and expiresAt > ?2",
            token, Instant.now()
        ).firstResult();

        if (session == null) {
            req.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity("Invalid or expired impersonation session").build());
            return;
        }

        // Impersonator must be the authenticated caller
        SecurityIdentity identity = identityInstance.get();
        String callerSub = identity.getAttribute("sub");
        String effectiveSub = (callerSub != null) ? callerSub : identity.getPrincipal().getName();

        if (!session.impersonator.keycloakSub.equals(effectiveSub)) {
            req.abortWith(Response.status(Response.Status.FORBIDDEN)
                .entity("Impersonation session belongs to a different user").build());
            return;
        }

        // Target must not be a site-tier account
        List<EffectivePermission> targetPerms = permissionService.getEffectivePermissions(session.target);
        boolean targetIsSiteTier = targetPerms.stream()
            .anyMatch(p -> AdminPermission.isSiteTierRole(p.role()));
        if (targetIsSiteTier) {
            req.abortWith(Response.status(Response.Status.FORBIDDEN)
                .entity("Cannot impersonate site-tier accounts").build());
            return;
        }

        ctx.setImpersonator(session.impersonator);
        ctx.setTarget(session.target);
        ctx.setSession(session);
        LOG.infof("Impersonation active: %s impersonating %s",
            session.impersonator.keycloakSub, session.target.keycloakSub);
    }
}
