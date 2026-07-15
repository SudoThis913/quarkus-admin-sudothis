package com.sudothis.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A permission template: assigning a UserType to an AppUser automatically grants
 * all PermissionGroups bundled in this type. Changing the type propagates to all
 * users who hold it.
 */
@Entity
@Table(name = "USER_TYPE")
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "User Types")
@org.hibernate.annotations.BatchSize(size = 50)
public class UserType extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String name;

    @Column(name = "display_name")
    public String displayName;

    @Column(length = 500)
    public String description;

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "created_by")
    public String createdBy;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "USER_TYPE_GROUP",
        joinColumns = @JoinColumn(name = "user_type_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_group_id")
    )
    public Set<PermissionGroup> permissionGroups = new HashSet<>();
}
