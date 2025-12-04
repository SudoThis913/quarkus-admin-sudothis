package com.sudothis.audit;

import com.sudothis.model.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuditService {

    /**
     * persist the audit log in a new transaction to avoid
     * ConcurrentModificationException in the parent entity's Hibernate flush cycle.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onAudit(@Observes AuditEvent event) {
        AuditLog.log(event.actor(), event.action(), event.targetTable(), event.targetId(), event.payload());
    }
}
