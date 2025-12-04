package com.sudothis.resource;

import com.sudothis.model.AppUser;
import io.smallrye.mutiny.Uni;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.List;

/**
 * REST endpoint that relies entirely on Quarkus‑OIDC for authentication & Keycloak for user management.
 * Only business data lives in the AppUser table – no passwords or hashes here.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppUserResource {

    @Inject
    OidcClient oidc;

    @Context
    SecurityIdentity identity;

    // ───────────────────────────────────────────────────────────────────── DTOs
    public record NewUser(String username, String email, String password, String roles) {}

    public record LoginRequest(String username, String password) {}

    public record TokenResponse(String access_token, String refresh_token, long expires_in) {}

    // ──────────────────────────────────────────────────────────────── Endpoints

    /** Admin‑only – create a new application user (business row only). */
    @POST
    @RolesAllowed("admin")
    @Operation(summary = "Create new user")
    public Uni<Response> create(NewUser dto) {
        // Persist business data; credentials are handled by Keycloak realm import / UI.
        AppUser.create(dto.username(), dto.email(), dto.password().toCharArray(), dto.roles());
        return Uni.createFrom().item(Response.status(Response.Status.CREATED).build());
    }

    /** Optional helper to exchange username/password for a JWT issued by Keycloak. */
    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "Username / password login (delegated to Keycloak)")
    public Uni<TokenResponse> login(LoginRequest req) {
        return oidc.getTokens(req.username(), req.password())
                   .onItem().transform(this::toTokenResponse);
    }

    /** List all users – requires admin realm‑role. */
    @GET
    @RolesAllowed("admin")
    @Operation(summary = "List all users", security = @SecurityRequirement(name = "jwt"))
    public Uni<List<AppUser>> list() {
        return AppUser.listAll();
    }

    /** Return details for the caller. */
    @GET
    @Path("/me")
    @RolesAllowed({"admin", "user"})
    @Operation(summary = "Who am I?", security = @SecurityRequirement(name = "jwt"))
    public Uni<AppUser> me() {
        return AppUser.find("username", identity.getPrincipal().getName()).singleResult();
    }

    /** Delete by id – admin only. */
    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    public Uni<Response> delete(@PathParam("id") Long id) {
        return Uni.createFrom()
                  .item(AppUser.deleteById(id))
                  .onItem().transform(deleted -> deleted ? Response.noContent().build()
                                                         : Response.status(Response.Status.NOT_FOUND).build());
    }

    // ─────────────────────────────────────────────────────────────── helpers
    private TokenResponse toTokenResponse(Tokens t) {
        return new TokenResponse(t.getAccessToken(), t.getRefreshToken(), t.getExpiresIn());
    }
}
