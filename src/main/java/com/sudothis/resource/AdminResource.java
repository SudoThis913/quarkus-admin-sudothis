// path: src/main/java/com/sudothis/resource/AdminResource.java
package com.sudothis.resource;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/admin")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class AdminResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    Template admin;

    @GET
    public TemplateInstance showDashboard() {
        String username = identity.getPrincipal().getName();
        String role = identity.getRoles().stream().findFirst().orElse("USER");
        return admin.data("username", username).data("role", role);
    }
}
