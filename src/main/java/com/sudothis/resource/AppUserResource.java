// src/main/java/com/sudothis/resource/AppUserResource.java
package com.sudothis.resource;

import com.sudothis.model.AppUser;
import com.sudothis.security.Argon2PasswordProvider;
import io.quarkus.security.Authenticated;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * REST endpoints for user management and authentication.
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking // ensure all methods run on a worker thread (Panache is blocking)
public class AppUserResource {

    @Inject SecurityIdentity currentUser;
    @Inject IdentityProviderManager idManager;

    // -------------------------------------------------------------- DTOs
    public record CreateUserRequest(@NotBlank String username,
                                     @Email String email,
                                     @NotBlank String password,
                                     @NotBlank String roles) {}
    public record UpdateUserRequest(@Email String email,
                                     String password,
                                     String roles) {}
    public record LoginRequest(String username, String password) {}
    public record TokenResponse(String token) {}

    // -------------------------------------------------------------- Queries
    @GET
    @RolesAllowed("admin")
    public List<AppUser> listAll() {
        return AppUser.listAll();
    }

    @GET
    @Path("/me")
    @Authenticated
    public Response me() {
        String uname = currentUser.getPrincipal().getName();
        AppUser user = AppUser.find("username", uname).firstResult();
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();
        user.passwordHash = null; // redact sensitive info
        return Response.ok(user).build();
    }

    // -------------------------------------------------------------- Create
    @POST
    @Transactional
    @RolesAllowed("admin")
    public Response create(@Valid CreateUserRequest req) {
        // Dup checks – simple but effective
        if (AppUser.find("username", req.username()).firstResult() != null)
            return Response.status(Response.Status.CONFLICT).entity("Username already exists").build();
        if (AppUser.find("email", req.email()).firstResult() != null)
            return Response.status(Response.Status.CONFLICT).entity("Email already exists").build();

        char[] pw = req.password().toCharArray();
        try {
            AppUser user = new AppUser();
            user.username      = req.username();
            user.email         = req.email();
            user.passwordHash  = Argon2PasswordProvider.hashPassword(pw);
            user.roles         = req.roles();
            user.verified       = true;                    // <-- allow immediate login in tests/dev
            user.persist();
        } finally {
            Arrays.fill(pw, '\0');
        }
        return Response.status(Response.Status.CREATED).build();
    }

    // -------------------------------------------------------------- Update
    @PUT
    @Path("/{id}")
    @Transactional
    @Authenticated
    public Response update(@PathParam("id") Long id, @Valid UpdateUserRequest req) {
        AppUser user = AppUser.findById(id);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();

        boolean isAdmin = currentUser.hasRole("admin");
        boolean isSelf  = currentUser.getPrincipal().getName().equals(user.username);
        if (!isAdmin && !isSelf) return Response.status(Response.Status.FORBIDDEN).build();

        if (req.email() != null)  user.setEmail(req.email());
        if (req.password() != null) {
            char[] pw = req.password().toCharArray();
            try { user.passwordHash = Argon2PasswordProvider.hashPassword(pw); }
            finally { Arrays.fill(pw, '\0'); }
        }
        if (isAdmin && req.roles() != null) user.roles = req.roles();
        return Response.ok().build();
    }

    // -------------------------------------------------------------- Delete
    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed("admin")
    public Response delete(@PathParam("id") Long id) {
        return AppUser.deleteById(id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    // -------------------------------------------------------------- Login
    @POST
    @Path("/login")
    @PermitAll
    public Uni<Response> login(LoginRequest req) {
        if (req.username() == null || req.password() == null)
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).build());

        char[] pw = req.password().toCharArray();
        UsernamePasswordAuthenticationRequest authReq = new UsernamePasswordAuthenticationRequest(
                req.username(), new PasswordCredential(pw));

        return idManager.authenticate(authReq)
                .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool()) // off‑load blocking work
                .onItem().transform(authIdentity -> {
                    Arrays.fill(pw, '\0');
                    if (authIdentity == null)
                        return Response.status(Response.Status.UNAUTHORIZED).build();

                    Set<String> roles = authIdentity.getRoles();
                    String token = Jwt.upn(authIdentity.getPrincipal().getName())
                                      .groups(roles)
                                      .sign();
                    return Response.ok(new TokenResponse(token)).build();
                });
    }
}
