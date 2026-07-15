package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * A named permission bundle: users in this group receive (defaultRole, scopeType, targetId).
 * SITE-scope groups have a null targetId. Intended to be composable — a user can be in
 * multiple groups, and UserTypes bundle groups into templates.
 */
@Entity
@Table(name = "PERMISSION_GROUP")
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "Permission Groups")
public class PermissionGroup extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String name;

    @Column(name = "display_name")
    public String displayName;

    @Column(length = 500)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_role", nullable = false)
    public AdminPermission.Role defaultRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    public AdminPermission.ScopeType scopeType;

    /** Null for SITE scope. org/company/team ID for scoped groups. */
    @Column(name = "target_id")
    public Long targetId;

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    /** keycloakSub of the admin who created this group. */
    @Column(name = "created_by")
    public String createdBy;
}
