package com.sudothis.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@org.hibernate.annotations.BatchSize(size = 50)
public class Team extends PanacheEntity implements TenantAware {

    @Column(nullable = false)
    public String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    public Company company;

    @JsonIgnore
    @ManyToMany(mappedBy = "teams")
    public Set<AppUser> members = new HashSet<>();

    @Override
    public Long getOrganizationId() {
        return company.organization != null ? company.organization.id : null;
    }

    @Override
    public Long getCompanyId() {
        return company.id;
    }

    @Override
    public Long getTeamId() {
        return id;
    }

    @Override public String jpqlOrganizationIdPath() { return "company.organization.id"; }
    @Override public String jpqlCompanyIdPath()      { return "company.id"; }
    @Override public String jpqlTeamIdPath()         { return "id"; }
}
