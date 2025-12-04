package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "TEAM")
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "Teams")
public class Team extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    public Organization organization;

    @ManyToMany(mappedBy = "teams")
    public Set<AppUser> members = new HashSet<>();
}
