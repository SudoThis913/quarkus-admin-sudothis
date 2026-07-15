package com.sudothis.resource;

import com.sudothis.model.AdminPermission;
import com.sudothis.model.AppUser;
import com.sudothis.model.ImpersonationSession;
import com.sudothis.security.EffectivePermission;
import com.sudothis.security.PermissionService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/api/admin/impersonation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class ImpersonationResource {

    private static final Logger LOG = Logger.getLogger(ImpersonationResource.class);
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

    public record StartImpersonationRequest(Long targetUserId, String reason) {}

    public record ImpersonationSessionDto(
        Long id, String sessionToken, Instant startedAt, Instant expiresAt,
        String impersonatorSub, String targetSub, String reason
    ) {}

    @Inject
    PermissionService permissionService;

    @Inject
    SecurityIdentity identity;

    @POST
    @Transactional
    public Response startImpersonation(StartImpersonationRequest req) {
        if (req.targetUserId() == null || req.reason() == null || req.reason().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("targetUserId and reason are required").build();
        }

        AppUser actor = permissionService.ensureActor(identity);

        // Actor must be site-tier
        List<EffectivePermission> actorPerms = permissionService.getEffectivePermissions(actor);
        boolean actorIsSiteTier = actorPerms.stream()
            .anyMatch(p -> AdminPermission.isSiteTierRole(p.role()));
        if (!actorIsSiteTier) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity("Only site-tier accounts may impersonate users").build();
        }

        AppUser target = AppUser.findById(req.targetUserId());
        if (target == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("Target user not found").build();
        }

        // Target must not be site-tier
        List<EffectivePermission> targetPerms = permissionService.getEffectivePermissions(target);
        boolean targetIsSiteTier = targetPerms.stream()
            .anyMatch(p -> AdminPermission.isSiteTierRole(p.role()));
        if (targetIsSiteTier) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity("Cannot impersonate site-tier accounts").build();
        }

        // Enforce SITE_ADMIN and SITE_AGENT cannot impersonate SITE_OWNER/SITE_ADMIN
        // (already handled by targetIsSiteTier check above)

        Instant now = Instant.now();
        ImpersonationSession session = new ImpersonationSession();
        session.impersonator = actor;
        session.target = target;
        session.reason = req.reason();
        session.startedAt = now;
        session.expiresAt = now.plus(SESSION_TIMEOUT);
        session.sessionToken = UUID.randomUUID().toString();
        session.active = true;
        session.persist();

        LOG.infof("Impersonation session started: %s impersonating %s (session %s)",
            actor.keycloakSub, target.keycloakSub, session.sessionToken);

        return Response.status(Response.Status.CREATED)
            .entity(toDto(session)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response endImpersonation(@PathParam("id") Long id) {
        ImpersonationSession session = ImpersonationSession.findById(id);
        if (session == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        AppUser actor = permissionService.ensureActor(identity);

        // Only the impersonator or a SITE_OWNER may end a session
        boolean isOwner = permissionService.getEffectivePermissions(actor).stream()
            .anyMatch(p -> p.role() == AdminPermission.Role.SITE_OWNER);
        if (!actor.keycloakSub.equals(session.impersonator.keycloakSub) && !isOwner) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity("Only the impersonator or SITE_OWNER may end this session").build();
        }

        session.endedAt = Instant.now();
        session.active = false;
        LOG.infof("Impersonation session %d ended by %s", id, actor.keycloakSub);

        return Response.noContent().build();
    }

    @GET
    @Path("/active")
    public Response listActiveSessions() {
        AppUser actor = permissionService.ensureActor(identity);

        List<EffectivePermission> perms = permissionService.getEffectivePermissions(actor);
        boolean canViewAll = perms.stream().anyMatch(p ->
            p.role() == AdminPermission.Role.SITE_OWNER ||
            p.role() == AdminPermission.Role.SITE_ADMIN);
        if (!canViewAll) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity("SITE_OWNER or SITE_ADMIN required").build();
        }

        List<ImpersonationSessionDto> sessions = ImpersonationSession
            .<ImpersonationSession>list("active = true and expiresAt > ?1", Instant.now())
            .stream()
            .map(this::toDto)
            .toList();

        return Response.ok(sessions).build();
    }

    private ImpersonationSessionDto toDto(ImpersonationSession s) {
        return new ImpersonationSessionDto(
            s.id, s.sessionToken, s.startedAt, s.expiresAt,
            s.impersonator.keycloakSub, s.target.keycloakSub, s.reason
        );
    }
}
