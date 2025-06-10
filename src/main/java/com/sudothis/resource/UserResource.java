// path: src/main/java/com/sudothis/resource/UserResource.java
package com.sudothis.resource;

import com.sudothis.model.AppUser;
import com.sudothis.security.AccessControlService;
import com.sudothis.service.UserService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
//import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
//import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    UserService userService;

    @Inject
    AccessControlService acl;

    @GET
    @Path("/{id}")
    public Response getUser(@PathParam("id") Long id) {
        AppUser actor = userService.findByUsername(identity.getPrincipal().getName()).orElseThrow();
        AppUser target = userService.findByIdOptional(id).orElseThrow();

        if (!acl.canEditUser(actor, target)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        return Response.ok(target).build();
    }

    
}
