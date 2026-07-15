package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

/**
 * Maps a specific JWT claim value to a local construct (PermissionGroup, UserType,
 * Organization, or Company). Evaluated after group mappings on each login.
 *
 * Example: provider=ENTRA, attributeName="department", attributeValue="Engineering",
 * targetType=USER_TYPE, targetId=5 → users in Entra's Engineering department get UserType 5.
 */
@Entity
@Table(name = "SSO_ATTRIBUTE_MAPPING")
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "SSO Attribute Mappings")
public class SsoAttributeMapping extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SsoProvider provider;

    /** JWT claim name to inspect (e.g., "department", "jobTitle", "hd"). */
    @Column(name = "attribute_name", nullable = false)
    public String attributeName;

    /** Value to match against. Case-sensitive string comparison. */
    @Column(name = "attribute_value", nullable = false)
    public String attributeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    public AttributeMappingTarget targetType;

    /** ID of the target entity (PermissionGroup.id, UserType.id, Organization.id, Company.id). */
    @Column(name = "target_id", nullable = false)
    public Long targetId;

    @Column(nullable = false)
    public boolean active = true;

    @Column(length = 500)
    public String description;

    public enum AttributeMappingTarget {
        PERMISSION_GROUP,
        USER_TYPE,
        ORGANIZATION,
        COMPANY
    }
}
