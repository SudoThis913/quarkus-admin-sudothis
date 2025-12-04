package com.sudothis.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "AUDIT_LOG")
public class AuditLog extends PanacheEntity {

    @Column(name = "ACTOR_ID")
    public String actorId;

    @Column(name = "ACTION", nullable = false)
    public String action; // CREATE, UPDATE, DELETE

    @Column(name = "TARGET_TABLE", nullable = false)
    public String targetTable;

    @Column(name = "TARGET_ID")
    public String targetId;

    @Column(name = "PAYLOAD", columnDefinition = "json")
    public String payload;

    @Column(name = "CREATED_AT", nullable = false)
    public Instant createdAt = Instant.now();

    public static void log(String actor, String action, String table, String id, String payload) {
        AuditLog log = new AuditLog();
        log.actorId = actor;
        log.action = action;
        log.targetTable = table;
        log.targetId = id;
        log.payload = payload;
        log.persist();
    }
}
