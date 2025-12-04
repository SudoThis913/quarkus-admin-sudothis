package com.sudothis.audit;

import com.sudothis.model.AuditLog;
import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.event.Event;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.Table;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;

public class AuditListener {

    private static final ObjectMapper mapper = new ObjectMapper();

    @PostPersist
    public void onPersist(Object entity) {
        publishEvent(entity, "CREATE");
    }

    @PostUpdate
    public void onUpdate(Object entity) {
        publishEvent(entity, "UPDATE");
    }

    @PostRemove
    public void onRemove(Object entity) {
        publishEvent(entity, "DELETE");
    }

    private void publishEvent(Object entity, String action) {
        if (entity instanceof AuditLog) return;

        String tableName = entity.getClass().getSimpleName();
        if (entity.getClass().isAnnotationPresent(Table.class)) {
            tableName = entity.getClass().getAnnotation(Table.class).name();
        }

        String id = getId(entity);
        String actor = getCurrentUser();
        String payload = serialize(entity);

        // Fire event to be handled asynchronously or in a new transaction
        try {
            // Look up the Event<AuditEvent> bean dynamically
            Event<AuditEvent> eventBus = Arc.container().beanManager().getEvent().select(AuditEvent.class);
            eventBus.fire(new AuditEvent(actor, action, tableName, id, payload));
            
        } catch (Exception e) {
            System.err.println("Failed to publish audit event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getCurrentUser() {
        try {
            SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
            if (identity != null && !identity.isAnonymous()) {
                return identity.getPrincipal().getName();
            }
        } catch (Exception e) {
            // Context not active
        }
        return "system/anonymous";
    }

    private String serialize(Object entity) {
        try {
            return mapper.writeValueAsString(entity);
        } catch (Exception e) {
            return "{\"error\": \"serialization_failed\"}";
        }
    }

    private String getId(Object entity) {
        try {
            Field idField = entity.getClass().getField("id");
            return String.valueOf(idField.get(entity));
        } catch (Exception e) {
             return "unknown";
        }
    }
}
