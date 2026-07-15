package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ADMIN_PERMISSION")
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "Permissions")
public class AdminPermission extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    public ScopeType scopeType;

    /** Null for SITE scope. Target org/company/team ID for scoped permissions. */
    @Column(name = "target_id")
    public Long targetId;

    /** keycloakSub of the admin who granted this permission. */
    @Column(name = "granted_by")
    public String grantedBy;

    @Column(name = "granted_at")
    public Instant grantedAt = Instant.now();

    /** Null = permanent. Set for time-limited grants. */
    @Column(name = "expires_at")
    public Instant expiresAt;

    /**
     * Non-null when this record was auto-generated from a PermissionGroup membership,
     * for traceability. Null = direct assignment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_group_id")
    public PermissionGroup sourceGroup;

    public enum Role {
        SITE_OWNER,
        SITE_ADMIN,
        SITE_AGENT,
        ORG_ADMIN,
        COMPANY_ADMIN,
        TEAM_ADMIN,
        USER
    }

    public enum ScopeType {
        SITE, ORGANIZATION, COMPANY, TEAM
    }

    /** Convenience: returns true if this role is a site-tier role. */
    public static boolean isSiteTierRole(Role role) {
        return role == Role.SITE_OWNER || role == Role.SITE_ADMIN || role == Role.SITE_AGENT;
    }

    // Transient — populated by AuditListener.@PreUpdate before Hibernate issues the SQL.
    // Allows the @PostUpdate handler to emit an old→new diff log line.
    @Transient public Role previousRole;
    @Transient public ScopeType previousScopeType;
    @Transient public Long previousTargetId;
}
