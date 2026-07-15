package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

/**
 * Maps an external SSO group (Entra group GUID, Google group email, Keycloak group name)
 * to a local PermissionGroup. When autoAssign=true the mapping is applied on every login.
 */
@Entity
@Table(
    name = "SSO_GROUP_MAPPING",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_sso_group_mapping_provider_external",
        columnNames = {"provider", "external_group_id"}
    )
)
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "SSO Group Mappings")
public class SsoGroupMapping extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SsoProvider provider;

    /** The external identifier: GUID for Entra, email for Google groups, name for Keycloak. */
    @Column(name = "external_group_id", nullable = false)
    public String externalGroupId;

    /** Human-readable label from the SSO provider — stored for display purposes only. */
    @Column(name = "external_group_name")
    public String externalGroupName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_group_id", nullable = false)
    public PermissionGroup localGroup;

    /** If true, add user to localGroup on every login where this external group is present. */
    @Column(name = "auto_assign", nullable = false)
    public boolean autoAssign = true;

    @Column(nullable = false)
    public boolean active = true;

    @Column(length = 500)
    public String description;
}
