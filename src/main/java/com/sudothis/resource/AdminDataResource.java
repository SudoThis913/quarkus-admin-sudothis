package com.sudothis.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sudothis.meta.DtoProjector;
import com.sudothis.meta.EntityDefinition;
import com.sudothis.meta.FieldDefinition;
import com.sudothis.meta.ModelRegistry;
import com.sudothis.model.AppUser;
import com.sudothis.security.FilterResult;
import com.sudothis.security.PermissionService;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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
import org.jboss.logging.Logger;

@Path("/api/admin/data")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminDataResource {

    private static final Logger LOG = Logger.getLogger(AdminDataResource.class);

    public final static Set<String> RESERVED_PARAMS = Set.of("page", "size", "sort");

    @Inject
    ModelRegistry registry;

    @Inject
    ObjectMapper mapper;

    @Inject
    EntityManager em;

    @Inject
    PermissionService permissions;

    @Inject
    SecurityIdentity identity;

    @Inject
    Validator validator;

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

        // Clamp pagination to safe bounds
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), 500);

        // --- Build Query ---
        StringBuilder queryBuilder = new StringBuilder("SELECT e FROM " + entityClass.getSimpleName() + " e");
        Map<String, List<String>> queryParams = uriInfo.getQueryParameters();
        Map<String, Object> params = new HashMap<>();
        List<String> whereClauses = new java.util.ArrayList<>();

        // Security Filtering (Multi-Tenancy)
        AppUser actor = permissions.ensureActor(identity);
        if (actor == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        FilterResult filter = permissions.getFilterClauses(actor, entityClass, "e");
        if (filter.denied()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        whereClauses.addAll(filter.whereClauses());
        params.putAll(filter.params());

        // Standard Filtering
        whereClauses.addAll(queryParams.entrySet().stream()
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
                .collect(Collectors.toList()));

        if (!whereClauses.isEmpty()) {
            queryBuilder.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        // Sorting — validated against per-entity allowlist of scalar fields
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
        EntityDefinition entityDef = registry.getDefinition(entityName);
        if (entityDef == null || !entityDef.sortableFields().contains(sortField)) {
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

        List<?> projected = results.stream()
                .map(e -> e instanceof DtoProjector dp ? dp.toDto() : e)
                .collect(Collectors.toList());

        return Response.ok(projected).build();
    }

    @GET
    @Path("/{entity}/{id}")
    public Response get(@PathParam("entity") String entityName, @PathParam("id") Long id) {
        Class<?> entityClass = registry.getEntityClass(entityName);
        if (entityClass == null) return Response.status(Response.Status.NOT_FOUND).build();

        Object entity = em.find(entityClass, id);
        if (entity == null) return Response.status(Response.Status.NOT_FOUND).build();

        AppUser getActor = permissions.ensureActor(identity);
        if (!permissions.canManage(getActor.keycloakSub, entityClass, id)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Object result = entity instanceof DtoProjector dp ? dp.toDto() : entity;
        return Response.ok(result).build();
    }

    @POST
    @Path("/{entity}")
    @Transactional
    public Response create(@PathParam("entity") String entityName, JsonNode payload) {
        LOG.infof("Creating entity %s with payload: %s", entityName, payload);
        Class<?> entityClass = registry.getEntityClass(entityName);
        if (entityClass == null) return Response.status(Response.Status.NOT_FOUND).build();

        try {
            preprocessPayload(entityName, payload);

            Object entity = mapper.treeToValue(payload, entityClass);
            Response invalid = validateEntity(entity);
            if (invalid != null) return invalid;

            em.persist(entity);
            LOG.infof("Successfully created %s: %s", entityName, entity);
            Object created = entity instanceof DtoProjector dp ? dp.toDto() : entity;
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to create %s", entityName);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{entity}/{id}")
    @Transactional
    public Response update(@PathParam("entity") String entityName, @PathParam("id") Long id, JsonNode payload) {
        LOG.infof("Updating entity %s ID %d with payload: %s", entityName, id, payload);
        Class<?> entityClass = registry.getEntityClass(entityName);
        if (entityClass == null) return Response.status(Response.Status.NOT_FOUND).build();

        Object existing = em.find(entityClass, id);
        if (existing == null) return Response.status(Response.Status.NOT_FOUND).build();

        AppUser putActor = permissions.ensureActor(identity);
        if (!permissions.canManage(putActor.keycloakSub, entityClass, id)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            preprocessPayload(entityName, payload);

            mapper.readerForUpdating(existing).readValue(payload);
            Response invalid = validateEntity(existing);
            if (invalid != null) return invalid;

            em.merge(existing);
            LOG.infof("Successfully updated %s ID %d", entityName, id);
            Object updated = existing instanceof DtoProjector dp ? dp.toDto() : existing;
            return Response.ok(updated).build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update %s ID %d", entityName, id);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    private void preprocessPayload(String entityName, JsonNode payload) {
        if (!(payload instanceof ObjectNode objNode)) return;
        EntityDefinition def = registry.getDefinition(entityName);
        if (def == null) return;

        for (FieldDefinition field : def.fields()) {
            if (field.reference() == null || field.list() || !objNode.has(field.name())) continue;

            JsonNode fieldNode = objNode.get(field.name());
            if (fieldNode == null || fieldNode.isNull()) continue;

            if (fieldNode.isValueNode()) {
                long refId = fieldNode.asLong(0);
                if (refId == 0) continue;
                validateReference(field.reference(), refId);
                ObjectNode relNode = mapper.createObjectNode();
                relNode.set("id", fieldNode);
                objNode.set(field.name(), relNode);
            } else if (fieldNode.isObject() && fieldNode.has("id")) {
                long refId = fieldNode.get("id").asLong(0);
                if (refId > 0) validateReference(field.reference(), refId);
            }
        }
    }

    private void validateReference(String refEntityName, long refId) {
        Class<?> refClass = registry.getEntityClass(refEntityName);
        if (refClass != null && em.find(refClass, refId) == null) {
            throw new IllegalArgumentException(
                refEntityName + " with ID " + refId + " does not exist"
            );
        }
    }

    @DELETE
    @Path("/{entity}/{id}")
    @Transactional
    public Response delete(@PathParam("entity") String entityName, @PathParam("id") Long id) {
        LOG.infof("Deleting entity %s ID %d", entityName, id);
        Class<?> entityClass = registry.getEntityClass(entityName);
        if (entityClass == null) return Response.status(Response.Status.NOT_FOUND).build();

        Object entity = em.find(entityClass, id);
        if (entity == null) return Response.status(Response.Status.NOT_FOUND).build();

        AppUser deleteActor = permissions.ensureActor(identity);
        if (!permissions.canManage(deleteActor.keycloakSub, entityClass, id)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        em.remove(entity);
        LOG.infof("Successfully deleted %s ID %d", entityName, id);
        return Response.noContent().build();
    }

    /** Returns a 422 response if the entity fails Bean Validation, null otherwise. */
    private Response validateEntity(Object entity) {
        var violations = validator.validate(entity);
        if (violations.isEmpty()) return null;
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        for (ConstraintViolation<?> v : violations) {
            errors.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return Response.status(422).entity(errors).build();
    }
}
