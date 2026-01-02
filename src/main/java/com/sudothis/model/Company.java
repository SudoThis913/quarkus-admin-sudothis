package com.sudothis.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "COMPANY")
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "Companies")
public class Company extends PanacheEntity implements TenantAware {

    @Column(nullable = false)
    public String name;

    public boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id") // Nullable for flat hierarchy
    public Organization organization;

    @JsonIgnore
    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    public Set<Team> teams = new HashSet<>();

    @JsonIgnore
    @ManyToMany(mappedBy = "companies")
    public Set<AppUser> users = new HashSet<>();

    @Override
    public Long getOrganizationId() {
        return organization != null ? organization.id : null;
    }

    @Override
    public Long getCompanyId() {
        return id;
    }

    @Override
    public Long getTeamId() {
        return null;
    }
}
