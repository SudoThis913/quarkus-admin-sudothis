package com.sudothis.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "PERSISTENT_SESSION")
public class PersistentSession {

    @Id
    @Column(name = "ID", length = 36)
    public String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    public AppUser user;

    @Column(name = "CSRF_TOKEN", nullable = false, length = 36)
    public String csrfToken;

    @Column(name = "IP_ADDRESS", length = 45)
    public String ipAddress;

    @Column(name = "CREATED_AT", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "EXPIRES_AT", nullable = false)
    public Instant expiresAt;

    public boolean expired() {
        return Instant.now().isAfter(expiresAt);
    }
}