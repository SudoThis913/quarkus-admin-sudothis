package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ORGANIZATION")
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "Organizations")
public class Organization extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String name;

    public boolean active = true;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    public Set<Team> teams = new HashSet<>();

    @OneToMany(mappedBy = "organization")
    public Set<AppUser> users = new HashSet<>();
}
