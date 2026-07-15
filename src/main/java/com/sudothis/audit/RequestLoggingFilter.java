package com.sudothis.audit;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.util.UUID;

/**
 * Sets MDC fields for structured JSON log output on every request.
 * Fields: userId (Keycloak sub), correlationId (X-Request-ID header or generated UUID).
 * Also logs access attempts at DEBUG (success) or WARN (denied) level for audit trails.
 */
@Provider
@Priority(Priorities.USER - 100)
public class RequestLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class);
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String CORRELATION_HEADER = "X-Request-ID";

    @Inject
    Instance<SecurityIdentity> identityInstance;

    @Override
    public void filter(ContainerRequestContext req) {
        String correlationId = req.getHeaderString(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_CORRELATION_ID, correlationId);
        req.setProperty(MDC_CORRELATION_ID, correlationId);

        try {
            SecurityIdentity identity = identityInstance.get();
            if (identity != null && !identity.isAnonymous()) {
                String sub = identity.getAttribute("sub");
                String userId = (sub != null) ? sub : identity.getPrincipal().getName();
                MDC.put(MDC_USER_ID, userId);
            }
        } catch (Exception ignored) {
            // SecurityIdentity not available outside request context
        }

        LOG.debugf("Incoming %s %s [correlationId=%s]",
            req.getMethod(), req.getUriInfo().getRequestUri(), correlationId);
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        int status = res.getStatus();
        String method = req.getMethod();
        String path = req.getUriInfo().getRequestUri().getPath();
        String correlationId = (String) req.getProperty(MDC_CORRELATION_ID);

        if (status == 401 || status == 403) {
            LOG.warnf("Access denied: %d %s %s [correlationId=%s]",
                status, method, path, correlationId);
        } else if (path.startsWith("/api/admin") && status >= 200 && status < 300) {
            LOG.debugf("Access granted: %d %s %s [correlationId=%s]",
                status, method, path, correlationId);
        }

        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_CORRELATION_ID);
    }
}
