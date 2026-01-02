package com.sudothis.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sudothis.meta.EntityDefinition;
import com.sudothis.meta.ModelRegistry;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/admin")
@RolesAllowed("admin")
public class AdminUiResource {

    @Inject
    Template admin;

    @Inject @Location("entity-list.html")
    Template entityList;

    @Inject @Location("entity-form.html")
    Template entityForm;

    @Inject
    ModelRegistry registry;

    @Inject
    ObjectMapper objectMapper;

    @Context
    UriInfo uriInfo;

    @Inject
    AdminDataResource adminDataResource;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getAdminPanel() {
        List<EntityDefinition> definitions = registry.getDefinitions();
        return admin.data("entityDefinitions", definitions);
    }

    @GET
    @Path("/{entityName}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getEntityList(@PathParam("entityName") String entityName,
                                          @QueryParam("page") @DefaultValue("0") int page,
                                          @QueryParam("size") @DefaultValue("20") int size,
                                          @QueryParam("sort") @DefaultValue("id") String sort) throws IOException {
        EntityDefinition def = registry.getDefinition(entityName);
        if (def == null) {
            throw new NotFoundException("Entity not found: " + entityName);
        }

        Response response = adminDataResource.list(entityName, uriInfo, page, size, sort);

        if (response.getStatus() != 200) {
            throw new WebApplicationException("Failed to fetch data", response.getStatus());
        }

        List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.getEntity();
        
        boolean hasNextPage = dataList.size() == size;
        int nextPage = page + 1;

        // Construct filter params for pagination links
        String filterParams = uriInfo.getQueryParameters().entrySet().stream()
                .filter(entry -> !AdminDataResource.RESERVED_PARAMS.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream().map(val -> entry.getKey() + "=" + val))
                .collect(Collectors.joining("&"));

        return entityList
                .data("entityName", entityName)
                .data("entityDefinition", def)
                .data("dataList", dataList)
                .data("hasNextPage", hasNextPage)
                .data("nextPage", nextPage)
                .data("currentPage", page)
                .data("sort", sort)
                .data("filterParams", filterParams);
    }

    @GET
    @Path("/{entityName}/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance createEntityForm(@PathParam("entityName") String entityName) {
        EntityDefinition def = registry.getDefinition(entityName);
        if (def == null) {
            throw new NotFoundException("Entity not found: " + entityName);
        }
        return entityForm
                .data("entityName", entityName)
                .data("entityDefinition", def)
                .data("entityData", null);
    }

    @GET
    @Path("/{entityName}/{id}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance editEntityForm(@PathParam("entityName") String entityName,
                                           @PathParam("id") Long id) throws IOException {
        EntityDefinition def = registry.getDefinition(entityName);
        if (def == null) {
            throw new NotFoundException("Entity not found: " + entityName);
        }

        Response response = adminDataResource.get(entityName, id);

        if (response.getStatus() == 404) {
             throw new NotFoundException("Entity item not found: " + id);
        }
        if (response.getStatus() != 200) {
            throw new WebApplicationException("Failed to fetch data", response.getStatus());
        }

        Object entityData = response.getEntity();

        return entityForm
                .data("entityName", entityName)
                .data("entityDefinition", def)
                .data("entityData", entityData);
    }

    @POST
    @Path("/{entityName}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response saveEntity(@PathParam("entityName") String entityName, MultivaluedMap<String, String> formData) {
        ObjectNode json = objectMapper.createObjectNode();
        formData.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                json.put(key, value.get(0));
            }
        });

        try {
            adminDataResource.create(entityName, json);
            // Re-render list but we need to fetch it. 
            // Since we want to return HTMX swap, we call getEntityList logic.
            // However, getEntityList returns TemplateInstance. Response builder can take it?
            // No, Response entity can be TemplateInstance.
            // But we need to add header.
            
            // Simplest: Redirect to list page (HTMX handles redirects if HX-Redirect header or standard 303)
            // Or return the list template directly.
            
            // Let's trigger a client-side event to show toast and reload list.
            return Response.ok()
                           .header("HX-Trigger", "{\"showMessage\": \"Created successfully\", \"reload-list\": \"true\"}")
                           .header("HX-Location", "/admin/" + entityName) // Force navigation/swap
                           .build();
        } catch (Exception e) {
             return Response.ok()
                           .header("HX-Trigger", "{\"showError\": \"Error creating entity: " + e.getMessage() + "\"}")
                           .build();
        }
    }

    @PUT
    @Path("/{entityName}/{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateEntity(@PathParam("entityName") String entityName, @PathParam("id") Long id, MultivaluedMap<String, String> formData) {
        ObjectNode json = objectMapper.createObjectNode();
        formData.forEach((key, value) -> {
            if (value != null && !value.isEmpty()) {
                json.put(key, value.get(0));
            }
        });

        try {
            adminDataResource.update(entityName, id, json);
            return Response.ok()
                           .header("HX-Trigger", "{\"showMessage\": \"Updated successfully\"}")
                           .header("HX-Location", "/admin/" + entityName)
                           .build();
        } catch (Exception e) {
             return Response.ok()
                           .header("HX-Trigger", "{\"showError\": \"Error updating entity: " + e.getMessage() + "\"}")
                           .build();
        }
    }
}
