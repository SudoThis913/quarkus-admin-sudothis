package com.sudothis.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sudothis.meta.EntityDefinition;
import com.sudothis.meta.FieldDefinition;
import com.sudothis.meta.ModelRegistry;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    @Inject
    AdminDataResource adminDataResource;

    @Context
    UriInfo uriInfo;

    // -------------------------------------------------------------------------
    // Page routes
    // -------------------------------------------------------------------------

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getAdminPanel() {
        return admin.data("entityDefinitions", registry.getDefinitions());
    }

    @GET
    @Path("/{entityName}")
    @Produces(MediaType.TEXT_HTML)
    @Transactional
    public TemplateInstance getEntityList(@PathParam("entityName") String entityName,
                                          @QueryParam("page") @DefaultValue("0") int page,
                                          @QueryParam("size") @DefaultValue("20") int size,
                                          @QueryParam("sort") @DefaultValue("id") String sort,
                                          @HeaderParam("HX-Request") String hxRequest) {
        redirectIfDirect(hxRequest);
        EntityDefinition def = registry.getDefinition(entityName);
        if (def == null) throw new NotFoundException("Entity not found: " + entityName);

        Response response = adminDataResource.list(entityName, uriInfo, page, size, sort);
        if (response.getStatus() != 200) {
            throw new WebApplicationException("Failed to fetch data", response.getStatus());
        }

        List<?> entities = (List<?>) response.getEntity();
        List<Map<String, Object>> dataList = toMaps(entities, def);

        // Parse sort into field + direction for template sort indicators
        String sortField = sort.startsWith("-") ? sort.substring(1) : sort;
        boolean sortDesc = sort.startsWith("-");

        String filterParams = uriInfo.getQueryParameters().entrySet().stream()
                .filter(e -> !AdminDataResource.RESERVED_PARAMS.contains(e.getKey()))
                .flatMap(e -> e.getValue().stream().map(v -> e.getKey() + "=" + v))
                .collect(Collectors.joining("&"));

        return entityList
                .data("entityName", entityName)
                .data("entityDefinition", def)
                .data("dataList", dataList)
                .data("hasNextPage", dataList.size() == size)
                .data("nextPage", page + 1)
                .data("currentPage", page)
                .data("sort", sort)
                .data("sortField", sortField)
                .data("sortDesc", sortDesc)
                .data("size", size)
                .data("filterParams", filterParams);
    }

    @GET
    @Path("/{entityName}/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance createEntityForm(@PathParam("entityName") String entityName,
                                             @HeaderParam("HX-Request") String hxRequest) {
        redirectIfDirect(hxRequest);
        EntityDefinition def = registry.getDefinition(entityName);
        if (def == null) throw new NotFoundException("Entity not found: " + entityName);
        return entityForm
                .data("entityName", entityName)
                .data("entityDefinition", def)
                .data("entityData", null);
    }

    @GET
    @Path("/{entityName}/{id}")
    @Produces(MediaType.TEXT_HTML)
    @Transactional
    public TemplateInstance editEntityForm(@PathParam("entityName") String entityName,
                                           @PathParam("id") Long id,
                                           @HeaderParam("HX-Request") String hxRequest) {
        redirectIfDirect(hxRequest);
        EntityDefinition def = registry.getDefinition(entityName);
        if (def == null) throw new NotFoundException("Entity not found: " + entityName);

        Response response = adminDataResource.get(entityName, id);
        if (response.getStatus() == 404) throw new NotFoundException("Entity not found: " + id);
        if (response.getStatus() != 200) throw new WebApplicationException("Failed to fetch entity", response.getStatus());

        Map<String, Object> entityData = toMap(response.getEntity(), def);

        return entityForm
                .data("entityName", entityName)
                .data("entityDefinition", def)
                .data("entityData", entityData);
    }

    // -------------------------------------------------------------------------
    // Mutation routes (form submissions)
    // -------------------------------------------------------------------------

    @POST
    @Path("/{entityName}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response saveEntity(@PathParam("entityName") String entityName,
                                MultivaluedMap<String, String> formData) {
        ObjectNode json = buildJson(formData);
        Response result = adminDataResource.create(entityName, json);
        if (result.getStatus() >= 400) {
            return Response.ok()
                    .header("HX-Trigger", hxTrigger("showError", extractError(result, "Create failed")))
                    .build();
        }
        return Response.ok()
                .header("HX-Trigger", hxTrigger("showMessage", "Created successfully"))
                .header("HX-Redirect", "/admin/" + entityName)
                .build();
    }

    @PUT
    @Path("/{entityName}/{id}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateEntity(@PathParam("entityName") String entityName,
                                  @PathParam("id") Long id,
                                  MultivaluedMap<String, String> formData) {
        ObjectNode json = buildJson(formData);
        Response result = adminDataResource.update(entityName, id, json);
        if (result.getStatus() >= 400) {
            return Response.ok()
                    .header("HX-Trigger", hxTrigger("showError", extractError(result, "Update failed")))
                    .build();
        }
        return Response.ok()
                .header("HX-Trigger", hxTrigger("showMessage", "Updated successfully"))
                .header("HX-Redirect", "/admin/" + entityName)
                .build();
    }

    @DELETE
    @Path("/{entityName}/{id}")
    public Response deleteEntity(@PathParam("entityName") String entityName,
                                  @PathParam("id") Long id) {
        Response result = adminDataResource.delete(entityName, id);
        if (result.getStatus() == 204) {
            // Return 200 with empty body — HTMX outerHTML swap removes the row
            return Response.ok()
                    .header("HX-Trigger", hxTrigger("showMessage", "Deleted"))
                    .build();
        }
        return Response.ok()
                .header("HX-Trigger", hxTrigger("showError", "Delete failed"))
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Redirects non-HTMX (direct browser) requests to /admin?path=... so the shell loads first. */
    private void redirectIfDirect(String hxRequest) {
        if (hxRequest != null) return;
        java.net.URI req = uriInfo.getRequestUri();
        String rel = req.getRawPath() + (req.getRawQuery() != null ? "?" + req.getRawQuery() : "");
        String encoded = URLEncoder.encode(rel, StandardCharsets.UTF_8);
        throw new WebApplicationException(
            Response.seeOther(URI.create("/admin?path=" + encoded)).build()
        );
    }

    /** Converts a list of JPA entities to Maps, flattening reference fields to their FK id. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMaps(List<?> entities, EntityDefinition def) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entity : entities) {
            result.add(toMap(entity, def));
        }
        return result;
    }

    /** Converts a single JPA entity to a Map, flattening reference fields to their FK id. */
    private Map<String, Object> toMap(Object entity, EntityDefinition def) {
        Map<String, Object> row = (Map<String, Object>) objectMapper.convertValue(entity, Map.class);
        for (FieldDefinition field : def.fields()) {
            if (field.reference() != null && !field.list() && row.containsKey(field.name())) {
                Object val = row.get(field.name());
                if (val instanceof Map<?, ?> refMap) {
                    row.put(field.name(), refMap.get("id"));
                }
            }
        }
        return row;
    }

    /** Builds a JSON ObjectNode from HTML form data. */
    private ObjectNode buildJson(MultivaluedMap<String, String> formData) {
        ObjectNode json = objectMapper.createObjectNode();
        formData.forEach((key, values) -> {
            if (values != null && !values.isEmpty() && !values.get(0).isBlank()) {
                json.put(key, values.get(0));
            }
        });
        return json;
    }

    /** Extracts a human-readable error message from a failed Response entity. */
    private String extractError(Response result, String fallback) {
        Object entity = result.getEntity();
        if (entity instanceof String s && !s.isBlank()) return s;
        if (entity instanceof Map<?, ?> map && !map.isEmpty()) {
            return map.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("; "));
        }
        return fallback + " (HTTP " + result.getStatus() + ")";
    }

    /** Builds a JSON HX-Trigger header value safely via Jackson. */
    private String hxTrigger(String event, String value) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put(event, value);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"" + event + "\": \"error\"}";
        }
    }
}
