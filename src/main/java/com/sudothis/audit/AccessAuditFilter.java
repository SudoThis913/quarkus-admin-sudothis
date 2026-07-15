package com.sudothis.audit;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Records all access attempts to /api/admin/** in the application log.
 *
 * 401/403 responses are logged at WARN — these feed alerting thresholds for HIPAA
 * 164.308(a)(5)(ii)(C) (failed login attempt monitoring).
 * Successful mutating operations (POST/PUT/DELETE 2xx) are logged at INFO.
 * Read operations (GET 2xx) are logged at DEBUG to avoid log volume explosion.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 1)
public class AccessAuditFilter implements ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(AccessAuditFilter.class);
    private static final String ADMIN_API_PREFIX = "/api/admin";

    @Inject
    Instance<SecurityIdentity> identityInstance;

    @Inject
    Event<AuditEvent> auditEvents;

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        String path = req.getUriInfo().getRequestUri().getPath();
        if (!path.startsWith(ADMIN_API_PREFIX)) return;

        int status = res.getStatus();
        String method = req.getMethod();
        String actor = resolveActor();
        String correlationId = (String) req.getProperty("correlationId");
        String sourceIp = req.getHeaderString("X-Forwarded-For");
        if (sourceIp == null) sourceIp = "unknown";

        if (status == 401 || status == 403) {
            LOG.warnf("[ACCESS-DENIED] actor=%s method=%s path=%s status=%d ip=%s correlationId=%s",
                actor, method, path, status, sourceIp, correlationId);
            // Persist denied attempts so ComplianceReportResource can report on them.
            // Volume is bounded: only auth failures are written, never successful traffic.
            try {
                String payload = String.format(
                    "{\"method\":\"%s\",\"path\":\"%s\",\"status\":%d,\"ip\":\"%s\",\"correlationId\":\"%s\"}",
                    method, path, status, sourceIp, correlationId);
                auditEvents.fire(new AuditEvent(actor, "ACCESS_DENIED", "ACCESS_ATTEMPT", path, payload));
            } catch (Exception e) {
                LOG.errorf(e, "Failed to persist denied-access audit event for %s %s", method, path);
            }
        } else if (status >= 400) {
            LOG.warnf("[ACCESS-ERROR] actor=%s method=%s path=%s status=%d ip=%s correlationId=%s",
                actor, method, path, status, sourceIp, correlationId);
        } else if (!"GET".equals(method)) {
            LOG.infof("[ACCESS-OK] actor=%s method=%s path=%s status=%d ip=%s correlationId=%s",
                actor, method, path, status, sourceIp, correlationId);
        } else {
            LOG.debugf("[ACCESS-OK] actor=%s method=%s path=%s status=%d ip=%s correlationId=%s",
                actor, method, path, status, sourceIp, correlationId);
        }
    }

    private String resolveActor() {
        try {
            SecurityIdentity identity = identityInstance.get();
            if (identity == null || identity.isAnonymous()) return "anonymous";
            String sub = identity.getAttribute("sub");
            return (sub != null) ? sub : identity.getPrincipal().getName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
