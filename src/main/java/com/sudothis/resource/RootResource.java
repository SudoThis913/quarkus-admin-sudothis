package com.sudothis.resource;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class RootResource {

    @Inject
    Template index;

    @Inject
    SecurityIdentity identity;

    /**
     * Landing page doubles as the login screen: anonymous visitors get a
     * sign-in card (the button starts the OIDC code flow via /admin, or the
     * form-auth flow in %h2 dev mode); authenticated users get a direct link
     * into the admin panel plus logout.
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getIndex() {
        boolean authenticated = !identity.isAnonymous();
        return index
            .data("authenticated", authenticated)
            .data("username", authenticated ? identity.getPrincipal().getName() : "");
    }
}
