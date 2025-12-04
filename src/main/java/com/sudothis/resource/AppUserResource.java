package com.sudothis.resource;

import com.sudothis.model.AppUser;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.List;

/**
 * REST endpoint for managing AppUser entities.
 * Uses blocking JAX-RS / Hibernate ORM.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppUserResource {

    @Inject
    SecurityIdentity identity;

    public record CreateUserDto(String username, String email, String keycloakSub) {}

    @POST
    @RolesAllowed("admin")
    @Operation(summary = "Create new user profile")
    @SecurityRequirement(name = "jwt")
    @jakarta.transaction.Transactional
    public Response create(CreateUserDto dto) {
        AppUser.ensureExists(dto.keycloakSub, dto.username, dto.email);
        return Response.status(Response.Status.CREATED).build();
    }

    /** List all users – requires admin realm‑role. */
    @GET
    @RolesAllowed("admin")
    @Operation(summary = "List all users")
    @SecurityRequirement(name = "jwt")
    public List<AppUser> list() {
        return AppUser.listAll();
    }

    /** Return details for the caller. */
    @GET
    @Path("/me")
    @RolesAllowed({"admin", "user"})
    @Operation(summary = "Who am I?")
    @SecurityRequirement(name = "jwt")
    public AppUser me() {
        String username = identity.getPrincipal().getName();
        // Ensure user exists in local DB (lazy provision)
        // Note: In a real scenario we should check the 'sub' claim
        // String sub = identity.getPrincipal().getClaim("sub");
        return AppUser.find("username", username).firstResult();
    }

    /** Delete by id – admin only. */
    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = AppUser.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }
}