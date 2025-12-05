package com.sudothis.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sudothis.meta.EntityDefinition;
import com.sudothis.meta.FieldDefinition;
import com.sudothis.meta.ModelRegistry;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/api/admin/data")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminDataResource {

    private static final Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort");

    @Inject
    ModelRegistry registry;

    @Inject
    ObjectMapper mapper;

    @Inject
    EntityManager em;

    @GET
    @Path("/{entity}")
    public Response list(@PathParam("entity") String entityName,
                         @Context UriInfo uriInfo,
                         @QueryParam("page") @DefaultValue("0") int page,
                         @QueryParam("size") @DefaultValue("20") int size,
                         @QueryParam("sort") String sort) {
        
        Class<?> entityClass = registry.getEntityClass(entityName);
        if (entityClass == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!PanacheEntityBase.class.isAssignableFrom(entityClass)) {
             return Response.serverError().entity("Entity does not extend PanacheEntityBase").build();
        }

        // --- Build Query ---
        StringBuilder queryBuilder = new StringBuilder("SELECT e FROM " + entityClass.getSimpleName() + " e");
        Map<String, List<String>> queryParams = uriInfo.getQueryParameters();
        Map<String, Object> params = new HashMap<>();

        // Filtering
        List<String> whereClauses = queryParams.entrySet().stream()
                .filter(entry -> !RESERVED_PARAMS.contains(entry.getKey()))
                .map(entry -> {
                    String paramName = entry.getKey();
                    String paramValue = entry.getValue().get(0); // Take first value for now

                    FieldDefinition fieldDef = registry.getDefinition(entityName).fields().stream()
                            .filter(f -> f.name().equals(paramName))
                            .findFirst().orElse(null);

                    if (fieldDef != null) {
                        // Basic type checking for filtering
                        if (fieldDef.type().equals("String")) {
                            params.put(paramName, "%" + paramValue + "%");
                            return "e." + paramName + " LIKE :" + paramName;
                        } else {
                            // Try to convert to appropriate type if possible
                            // For simplicity, for non-string, assume exact match
                            params.put(paramName, paramValue);
                            return "e." + paramName + " = :" + paramName;
                        }
                    }
                    return null; // Ignore unknown fields for filtering
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        if (!whereClauses.isEmpty()) {
            queryBuilder.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        // Sorting
        String sortField = "id";
        String sortOrder = "ASC";
        if (sort != null && !sort.isBlank()) {
            if (sort.startsWith("-")) {
                sortField = sort.substring(1);
                sortOrder = "DESC";
            } else {
                sortField = sort;
                sortOrder = "ASC";
            }
        }
        // Basic prevention of SQL Injection on sortField (simple regex)
        if (!sortField.matches("^[a-zA-Z0-9_.]+$")) {
            sortField = "id";
        }
        queryBuilder.append(" ORDER BY e.").append(sortField).append(" ").append(sortOrder);
        
        // Execute Query
        jakarta.persistence.Query query = em.createQuery(queryBuilder.toString());
        params.forEach((key, value) -> {
            // Need to convert value type for non-strings for JPQL parameters
            FieldDefinition fieldDef = registry.getDefinition(entityName).fields().stream()
                    .filter(f -> f.name().equals(key))
                    .findFirst().orElse(null);
            
            if (fieldDef != null) {
                 try {
                    Object convertedValue = value;
                    if (fieldDef.type().equals("Long")) {
                        convertedValue = Long.valueOf((String)value);
                    } else if (fieldDef.type().equals("Integer")) {
                        convertedValue = Integer.valueOf((String)value);
                    } else if (fieldDef.type().equals("Boolean")) {
                        convertedValue = Boolean.valueOf((String)value);
                    }
                    // Add more types as needed
                    query.setParameter(key, convertedValue);

                 } catch (NumberFormatException e) {
                     // Log error, or just let it pass if type mismatch is expected to fail query.
                     // For MVP, if conversion fails, the query might throw an exception later.
                 }
            } else {
                query.setParameter(key, value); // Fallback for unknown fields (shouldn't happen with filtering)
            }
        });

        List<?> results = query
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        return Response.ok(results).build();
    }

    @GET
    @Path("/{entity}/{id}")
    public Response get(@PathParam("entity") String entityName, @PathParam("id") Long id) {
        Class<?> entityClass = registry.getEntityClass(entityName);
        if (entityClass == null) return Response.status(Response.Status.NOT_FOUND).build();

        Object entity = em.find(entityClass, id);
        if (entity == null) return Response.status(Response.Status.NOT_FOUND).build();

        return Response.ok(entity).build();
    }

    @POST
    @Path("/{entity}")
    @Transactional
    public Response create(@PathParam("entity") String entityName, JsonNode payload) {
        Class<?> entityClass = registry.getEntityClass(entityName);
        if (entityClass == null) return Response.status(Response.Status.NOT_FOUND).build();

        try {
            preprocessPayload(entityName, payload);
            
            Object entity = mapper.treeToValue(payload, entityClass);
            em.persist(entity);
            return Response.status(Response.Status.CREATED).entity(entity).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{entity}/{id}")
    @Transactional
    public Response update(@PathParam("entity") String entityName, @PathParam("id") Long id, JsonNode payload) {
        Class<?> entityClass = registry.getEntityClass(entityName);
        if (entityClass == null) return Response.status(Response.Status.NOT_FOUND).build();

        Object existing = em.find(entityClass, id);
        if (existing == null) return Response.status(Response.Status.NOT_FOUND).build();

        try {
            preprocessPayload(entityName, payload);
            
            mapper.readerForUpdating(existing).readValue(payload);
            em.merge(existing); 
            return Response.ok(existing).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    private void preprocessPayload(String entityName, JsonNode payload) {
        if (payload instanceof ObjectNode) {
            ObjectNode objNode = (ObjectNode) payload;
            EntityDefinition def = registry.getDefinition(entityName);
            if (def == null) return;

            for (FieldDefinition field : def.fields()) {
                if (field.reference() != null && !field.list() && objNode.has(field.name())) {
                    JsonNode fieldNode = objNode.get(field.name());
                    if (fieldNode.isValueNode()) {
                        ObjectNode relNode = mapper.createObjectNode();
                        relNode.set("id", fieldNode);
                        objNode.set(field.name(), relNode);
                    }
                }
            }
        }
    }

    @DELETE
    @Path("/{entity}/{id}")
    @Transactional
    public Response delete(@PathParam("entity") String entityName, @PathParam("id") Long id) {
        Class<?> entityClass = registry.getEntityClass(entityName);
        if (entityClass == null) return Response.status(Response.Status.NOT_FOUND).build();

        Object entity = em.find(entityClass, id);
        if (entity == null) return Response.status(Response.Status.NOT_FOUND).build();
        
        em.remove(entity);
        return Response.noContent().build();
    }
}
