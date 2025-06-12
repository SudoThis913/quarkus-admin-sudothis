package com.sudothis.resource;

import com.sudothis.model.AppUser;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import java.util.List;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppUserResource {

    @Inject
    SecurityIdentity identity;

    public static class CreateUserRequest {
        public String username;
        public String password;
        public String role;
    }

    public static class UpdateUserRequest {
        public String email;
        public String password;
        public String role;
    }

    public static class LoginRequest {
        public String username;
        public String password;
    }

    // --------- GET /api/users (Admin only) ---------
    @GET
    @RolesAllowed("SITE_ADMIN")
    public List<AppUser> listAll() {
        return AppUser.listAll();
    }

    // --------- GET /api/users/me (Authenticated) ---------
    @GET
    @Path("/me")
    @Authenticated
    public Response me() {
        String username = identity.getPrincipal().getName();
        AppUser user = AppUser.find("username", username).firstResult();
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        user.password = null; // avoid leaking hash
        return Response.ok(user).build();
    }

    // --------- POST /api/users (Admin only) ---------
    @POST
    @Transactional
    @RolesAllowed("SITE_ADMIN")
    public Response createUser(CreateUserRequest newUser) {
        if (newUser.username == null || newUser.password == null || newUser.role == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing required fields").build();
        }

        char[] rawPassword = newUser.password.toCharArray();
        try {
            AppUser.add(newUser.username, rawPassword, newUser.role);
        } finally {
            java.util.Arrays.fill(rawPassword, '\0');
        }

        return Response.status(Response.Status.CREATED).build();
    }

    // --------- PUT /api/users/{id} (Admin or self) ---------
    @PUT
    @Path("/{id}")
    @Transactional
    @Authenticated
    public Response updateUser(@PathParam("id") Long id, UpdateUserRequest updatedData) {
        AppUser user = AppUser.findById(id);
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        boolean isAdmin = identity.hasRole("SITE_ADMIN");
        boolean isSelf = identity.getPrincipal().getName().equals(user.username);
        if (!isAdmin && !isSelf) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (updatedData.email != null) user.setEmail(updatedData.email);
        if (updatedData.password != null) {
            char[] rawPassword = updatedData.password.toCharArray();
            try {
                user.setPassword(user.hashPassword(rawPassword));
            } finally {
                java.util.Arrays.fill(rawPassword, '\0');
            }
        }
        if (isAdmin && updatedData.role != null) user.setRole(updatedData.role);

        return Response.ok().build();
    }

    // --------- DELETE /api/users/{id} (Admin only) ---------
    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed("SITE_ADMIN")
    public Response deleteUser(@PathParam("id") Long id) {
        boolean deleted = AppUser.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    // --------- POST /api/users/login ---------
    @POST
    @Path("/login")
    @PermitAll
    public Response login(LoginRequest request) {
        if (request.username == null || request.password == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing credentials").build();
        }

        AppUser user = AppUser.find("username", request.username).firstResult();
        if (user == null || !user.isActive()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid credentials").build();
        }

        char[] rawPassword = request.password.toCharArray();
        boolean valid;
        try {
            valid = user.checkPassword(rawPassword);
        } finally {
            java.util.Arrays.fill(rawPassword, '\0');
        }

        if (!valid) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid credentials").build();
        }

        user.password = null; // scrub hash
        return Response.ok(user).build();
    }
}
