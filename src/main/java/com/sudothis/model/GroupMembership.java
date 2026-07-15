package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks a user's membership in a PermissionGroup, including who granted it and when.
 * A null expiresAt means the membership is permanent.
 */
@Entity
@Table(
    name = "GROUP_MEMBERSHIP",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_group_membership_user_group",
        columnNames = {"user_id", "group_id"}
    )
)
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "Group Memberships")
public class GroupMembership extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    public AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    public PermissionGroup group;

    /** keycloakSub of the admin who granted membership. */
    @Column(name = "granted_by")
    public String grantedBy;

    @Column(name = "granted_at", nullable = false)
    public Instant grantedAt = Instant.now();

    /** Null = permanent membership. */
    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(nullable = false)
    public boolean active = true;
}
