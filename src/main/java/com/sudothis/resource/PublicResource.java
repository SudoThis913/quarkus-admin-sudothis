// src/main/java/com/sudothis/resource/PublicResource.java
package com.sudothis.resource;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/public")
public class PublicResource {

    // ------------------------------------------------------------
    // Qute template binding for the login form
    // ------------------------------------------------------------
    @CheckedTemplate
    static class Templates {
        public static native TemplateInstance login(); // maps to templates/login.html
    }

    // --------- GET /api/public (plain‑text health/ping) ----------
    @GET
    @PermitAll
    @Produces(MediaType.TEXT_PLAIN)
    public String publicResource() {
        return "public";
    }

    // --------- GET /login (HTML form) ---------------------------
    @GET
    @Path("/login")
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance loginPage() {
        return Templates.login();
    }
}
