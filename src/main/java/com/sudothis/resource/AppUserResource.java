package com.sudothis.resource;

import com.sudothis.model.AppUser;
import com.sudothis.resource.dto.AppUserDto;
import com.sudothis.security.PermissionService;
import com.sudothis.security.SsoClaimProcessor;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppUserResource {

    private static final Logger LOG = Logger.getLogger(AppUserResource.class);

    @Inject
    SecurityIdentity identity;

    @Inject
    PermissionService permissions;

    @Inject
    SsoClaimProcessor ssoClaimProcessor;

    public record CreateUserDto(String username, String email, String keycloakSub) {}

    @POST
    @RolesAllowed("admin")
    @Operation(summary = "Create new user profile")
    @SecurityRequirement(name = "jwt")
    @Transactional
    public Response create(CreateUserDto dto) {
        AppUser user = AppUser.ensureExists(dto.keycloakSub, dto.username, dto.email);
        permissions.validateEmployeeConstraints(user);
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @RolesAllowed("admin")
    @Operation(summary = "List all users")
    @SecurityRequirement(name = "jwt")
    @Transactional
    public List<AppUserDto> list() {
        return AppUser.<AppUser>listAll().stream()
            .map(AppUserDto::from)
            .collect(Collectors.toList());
    }

    @GET
    @Path("/me")
    @RolesAllowed({"admin", "user"})
    @Operation(summary = "Who am I?")
    @SecurityRequirement(name = "jwt")
    @Transactional
    public Response me() {
        String sub = com.sudothis.security.Subjects.of(identity);
        if (sub == null) {
            LOG.warn("No subject resolvable for /me request");
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity("Missing subject claim").build();
        }
        String username = identity.getPrincipal().getName();
        String email = identity.getAttribute("email");
        AppUser user = AppUser.ensureExists(sub, username, email);
        ssoClaimProcessor.processLogin(user, identity);
        return Response.ok(AppUserDto.from(user)).build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = AppUser.deleteById(id);
        return deleted
            ? Response.noContent().build()
            : Response.status(Response.Status.NOT_FOUND).build();
    }
}
