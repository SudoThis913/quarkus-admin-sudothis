package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

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

    @Column(name = "target_id", nullable = false)
    public Long targetId;

    public enum Role {
        SITE_OWNER, SITE_ADMIN, ORG_ADMIN, COMPANY_ADMIN, TEAM_ADMIN
    }

    public enum ScopeType {
        SITE, ORGANIZATION, COMPANY, TEAM
    }
}
