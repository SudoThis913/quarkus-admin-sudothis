package com.sudothis.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import com.sudothis.meta.DtoProjector;
import com.sudothis.resource.dto.AppUserDto;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight user profile that complements Keycloak-based authentication.
 * Keycloak/OIDC is the single source of truth for identity and roles; we store
 * only service-local data. The record is created lazily on first token encounter.
 */
@Entity
@Table(name = "APP_USER")
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "Users")
@org.hibernate.annotations.BatchSize(size = 50)
public class AppUser extends PanacheEntity implements TenantAware, DtoProjector {

    @Column(name = "USERNAME", nullable = false)
    public String username;

    /** Immutable subject ID from Keycloak/OIDC. Used as the canonical actor identifier. */
    @Column(name = "KEYCLOAK_SUB", unique = true)
    public String keycloakSub;

    @Email
    @Column(name = "EMAIL")
    public String email;

    @Column(name = "CREATED_AT", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "IS_ACTIVE")
    public boolean active = true;

    /** Flag: employees belong to exactly one company (enforced by PermissionService). */
    public boolean isEmployee;

    /**
     * For employee users, this is their single company — used for TenantAware resolution.
     * Customer users may have many companies (ManyToMany below) and leave this null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_company_id")
    public Company primaryCompany;

    /** Optional assignment of this user to a UserType permission template. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_type_id")
    public UserType userType;

    /**
     * Legacy direct org link — kept for backward compatibility.
     * Prefer resolving org via primaryCompany for employees.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    public Organization organization;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "USER_COMPANY",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "company_id")
    )
    public Set<Company> companies = new HashSet<>();

    @JsonIgnore
    @ManyToMany
    @JoinTable(
        name = "USER_TEAM",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    public Set<Team> teams = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    public Set<GroupMembership> groupMemberships = new HashSet<>();

    // -------------------------------------------------------------------------
    // TenantAware — resolve tenant context for permission filtering
    // -------------------------------------------------------------------------

    @Override
    public Long getOrganizationId() {
        if (primaryCompany != null && primaryCompany.organization != null) {
            return primaryCompany.organization.id;
        }
        if (organization != null) {
            return organization.id;
        }
        return null;
    }

    @Override
    public Long getCompanyId() {
        return primaryCompany != null ? primaryCompany.id : null;
    }

    @Override
    public Long getTeamId() {
        // Users can belong to multiple teams; filtering by group membership handles multi-team
        return null;
    }

    @Override public String jpqlOrganizationIdPath() { return "organization.id"; }
    @Override public String jpqlCompanyIdPath()      { return "primaryCompany.id"; }

    // -------------------------------------------------------------------------
    // Factory helpers
    // -------------------------------------------------------------------------

    @Override
    public Object toDto() {
        return AppUserDto.from(this);
    }

    public static AppUser ensureExists(String keycloakSub,
                                       String preferredUsername,
                                       String email) {
        AppUser existing = find("keycloakSub", keycloakSub).firstResult();
        if (existing != null) {
            return existing;
        }
        AppUser user = new AppUser();
        user.keycloakSub = keycloakSub;
        user.username    = preferredUsername;
        user.email       = email;
        user.persist();
        return user;
    }
}
