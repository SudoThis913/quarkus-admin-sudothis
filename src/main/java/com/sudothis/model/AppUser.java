// src/main/java/com/sudothis/model/AppUser.java
package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import java.time.Instant;

/**
 * Lightweight user profile that complements Keycloak‑based authentication.
 * <p>
 * Keycloak/OIDC is the single source of truth for identity and roles, so we
 * store only the data that is <em>local‑to‑this‑service</em> (timestamps,
 * preferences, feature flags …).  The record is created lazily the first time
 * we see a Keycloak token with an unknown {@code sub} claim.
 */
@Entity
@Table(name = "APP_USER")
@EntityListeners(AuditListener.class)
public class AppUser extends PanacheEntity {

    /** Copied from Keycloak's {@code preferred_username} claim for convenience. */
    @Column(name = "USERNAME", nullable = false)
    public String username;

    /** The unique subject ID from Keycloak/OIDC token. */
    @Column(name = "KEYCLOAK_SUB", unique = true)
    public String keycloakSub;

    /** Optional – only stored if realm is configured to include e‑mail in ID token. */
    @Email
    @Column(name = "EMAIL")
    public String email;

    @Column(name = "CREATED_AT", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "IS_ACTIVE")
    public boolean active = true;

    // ---------------------------------------------------------------------
    // Factory helpers
    // ---------------------------------------------------------------------

    /**
     * Provision (or fetch) a local profile from Keycloak token attributes.
     */
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
