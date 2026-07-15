package com.sudothis.resource;

import com.sudothis.model.AdminPermission;
import com.sudothis.model.AppUser;
import com.sudothis.model.AuditLog;
import com.sudothis.security.PermissionService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compliance evidence endpoints for HIPAA / SOC2 auditors.
 *
 * All endpoints are restricted to site-tier actors (SITE_OWNER / SITE_ADMIN /
 * SITE_AGENT): these reports span every tenant, so scoped admins must not see
 * them. Results are read-only aggregates — no report endpoint mutates state.
 */
@Path("/api/admin/compliance")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class ComplianceReportResource {

    @Inject
    PermissionService permissions;

    @Inject
    SecurityIdentity identity;

    public record RoleMatrixEntry(
        Long userId, String username, String keycloakSub,
        AdminPermission.Role role, AdminPermission.ScopeType scopeType, Long targetId,
        String grantedBy, Instant grantedAt, Instant expiresAt, String sourceGroup) {}

    public record DailyCount(String actor, String action, LocalDate day, long count) {}

    /** Current role matrix: who holds which role at which scope. */
    @GET
    @Path("/roles")
    @Transactional
    public Response roles() {
        Response denied = requireSiteTier();
        if (denied != null) return denied;

        List<RoleMatrixEntry> matrix = AdminPermission.<AdminPermission>listAll().stream()
            .map(p -> new RoleMatrixEntry(
                p.user != null ? p.user.id : null,
                p.user != null ? p.user.username : null,
                p.user != null ? p.user.keycloakSub : null,
                p.role, p.scopeType, p.targetId,
                p.grantedBy, p.grantedAt, p.expiresAt,
                p.sourceGroup != null ? p.sourceGroup.name : null))
            .toList();
        return Response.ok(matrix).build();
    }

    /** CREATE/UPDATE/DELETE counts per actor per day over the requested window. */
    @GET
    @Path("/audit-summary")
    @Transactional
    public Response auditSummary(@QueryParam("days") @DefaultValue("30") int days) {
        Response denied = requireSiteTier();
        if (denied != null) return denied;
        return Response.ok(aggregate(
            "action in ('CREATE','UPDATE','DELETE') and createdAt >= ?1", clampWindow(days))).build();
    }

    /** Denied access attempts (401/403) per actor per day, from persisted ACCESS_DENIED events. */
    @GET
    @Path("/denied-attempts")
    @Transactional
    public Response deniedAttempts(@QueryParam("days") @DefaultValue("30") int days) {
        Response denied = requireSiteTier();
        if (denied != null) return denied;
        return Response.ok(aggregate(
            "action = 'ACCESS_DENIED' and createdAt >= ?1", clampWindow(days))).build();
    }

    private static Instant clampWindow(int days) {
        int clamped = Math.min(Math.max(1, days), 365);
        return Instant.now().minus(clamped, ChronoUnit.DAYS);
    }

    /**
     * Aggregates in-memory rather than with DB date functions so the same code
     * runs on MySQL (prod) and H2 (tests). Row volume is bounded by the audit
     * retention window and single-node deployment scale.
     */
    private List<DailyCount> aggregate(String query, Instant since) {
        Map<String, Long> buckets = new TreeMap<>();
        for (AuditLog log : AuditLog.<AuditLog>list(query, since)) {
            LocalDate day = log.createdAt.atZone(ZoneOffset.UTC).toLocalDate();
            String key = log.actorId + "|" + log.action + "|" + day;
            buckets.merge(key, 1L, Long::sum);
        }
        return buckets.entrySet().stream()
            .map(e -> {
                String[] parts = e.getKey().split("\\|", 3);
                return new DailyCount(parts[0], parts[1], LocalDate.parse(parts[2]), e.getValue());
            })
            .toList();
    }

    /** Returns a 403 response unless the current actor holds a site-tier role. */
    private Response requireSiteTier() {
        AppUser actor = permissions.ensureActor(identity);
        if (actor == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        boolean siteTier = permissions.getEffectivePermissions(actor).stream()
            .anyMatch(p -> AdminPermission.isSiteTierRole(p.role()));
        if (!siteTier) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", "Compliance reports require a site-tier role"))
                .build();
        }
        return null;
    }
}
