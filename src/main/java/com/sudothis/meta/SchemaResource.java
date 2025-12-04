package com.sudothis.meta;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/admin/schema")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class SchemaResource {

    @Inject
    ModelRegistry registry;

    @GET
    public List<EntityDefinition> getSchema() {
        return registry.getDefinitions();
    }

    @GET
    @Path("/{name}")
    public EntityDefinition getEntitySchema(@PathParam("name") String name) {
        return registry.getDefinition(name);
    }
}
