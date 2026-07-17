package com.sudothis.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudothis.model.AdminPermission;
import com.sudothis.model.AuditLog;
import com.sudothis.security.ImpersonationContext;
import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.jboss.logging.Logger;

public class AuditListener {

    private static final Logger LOG = Logger.getLogger(AuditListener.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @PostPersist
    public void onPersist(Object entity) {
        publishEvent(entity, "CREATE");
    }

    /**
     * Runs before Hibernate issues the UPDATE SQL. For AdminPermission, reads the
     * pre-commit DB state via a StatelessSession (bypasses first-level cache) and
     * stores old role/scope/targetId in transient fields so @PostUpdate can diff them.
     */
    @PreUpdate
    public void onPreUpdate(Object entity) {
        if (!(entity instanceof AdminPermission perm) || perm.id == null) return;
        try {
            EntityManagerFactory emf = Arc.container().instance(EntityManagerFactory.class).get();
            SessionFactory sf = emf.unwrap(SessionFactory.class);
            try (StatelessSession ss = sf.openStatelessSession()) {
                AdminPermission old = ss.get(AdminPermission.class, perm.id);
                if (old != null) {
                    perm.previousRole      = old.role;
                    perm.previousScopeType = old.scopeType;
                    perm.previousTargetId  = old.targetId;
                }
            }
        } catch (Exception e) {
            LOG.debugf("Could not snapshot old permission state for id=%s: %s", perm.id, e.getMessage());
        }
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

        // Structured application-log line for permission changes — security teams can alert on [PERMISSION-*]
        if (entity instanceof AdminPermission perm) {
            logPermissionEvent(perm, action, actor);
        }

        try {
            Event<AuditEvent> eventBus = Arc.container().beanManager().getEvent().select(AuditEvent.class);
            eventBus.fire(new AuditEvent(actor, action, tableName, id, payload));
        } catch (Exception e) {
            // Use stderr as last resort — Logger itself may not be available if CDI context is gone
            LOG.errorf(e, "Failed to publish audit event for %s %s", action, tableName);
        }
    }

    private void logPermissionEvent(AdminPermission perm, String action, String actor) {
        String userSub = "unknown";
        try {
            if (perm.user != null) userSub = perm.user.keycloakSub;
        } catch (Exception ignored) {}

        if ("UPDATE".equals(action) && perm.previousRole != null) {
            LOG.warnf("[PERMISSION-CHANGE] actor=%s user=%s %s/%s/%s → %s/%s/%s",
                actor, userSub,
                perm.previousRole, perm.previousScopeType, perm.previousTargetId,
                perm.role, perm.scopeType, perm.targetId);
        } else if ("DELETE".equals(action)) {
            LOG.warnf("[PERMISSION-REVOKE] actor=%s user=%s role=%s scope=%s targetId=%s",
                actor, userSub, perm.role, perm.scopeType, perm.targetId);
        } else {
            LOG.warnf("[PERMISSION-%s] actor=%s user=%s role=%s scope=%s targetId=%s",
                action, actor, userSub, perm.role, perm.scopeType, perm.targetId);
        }
    }

    private String getCurrentUser() {
        try {
            SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
            if (identity == null || identity.isAnonymous()) {
                return "system/anonymous";
            }

            String effectiveSub = com.sudothis.security.Subjects.of(identity);
            if (effectiveSub == null) {
                return "system/anonymous";
            }

            try {
                ImpersonationContext impCtx = Arc.container().instance(ImpersonationContext.class).get();
                if (impCtx != null && impCtx.isActive()) {
                    String targetSub = impCtx.getTarget().keycloakSub;
                    return effectiveSub + " [impersonating " + targetSub + "]";
                }
            } catch (Exception ignored) {
                // ImpersonationContext not active in this request scope
            }

            return effectiveSub;
        } catch (Exception e) {
            return "system/anonymous";
        }
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
