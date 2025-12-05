package com.sudothis.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudothis.meta.ModelRegistry;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/admin/data")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminDataResource {

    @Inject
    ModelRegistry registry;

    @Inject
    ObjectMapper mapper;

    @Inject
    EntityManager em;

    @GET
    @Path("/{entity}")
    public Response list(@PathParam("entity") String entityName,
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

        // Dynamic Querying using standard JPA to avoid static generic issues with Panache
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

        String qlString = "SELECT e FROM " + entityClass.getSimpleName() + " e ORDER BY e." + sortField + " " + sortOrder;
        
        List<?> results = em.createQuery(qlString)
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
            mapper.readerForUpdating(existing).readValue(payload);
            em.merge(existing); 
            return Response.ok(existing).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
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