package com.sudothis.model;

import com.sudothis.audit.AuditListener;
import com.sudothis.meta.AdminResource;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks an active impersonation session. Only SITE_OWNER, SITE_ADMIN, and SITE_AGENT
 * may create sessions. Target must be a non-site-tier account.
 * Sessions auto-expire after a configurable timeout (default 30 minutes).
 * The sessionToken is sent by clients via the X-Impersonation-Session HTTP header.
 */
@Entity
@Table(name = "IMPERSONATION_SESSION")
@EntityListeners(AuditListener.class)
@AdminResource(displayName = "Impersonation Sessions", readOnly = true)
public class ImpersonationSession extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impersonator_id", nullable = false)
    public AppUser impersonator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    public AppUser target;

    @Column(nullable = false, length = 500)
    public String reason;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt = Instant.now();

    /** Null means session is still active. Set when session is explicitly ended. */
    @Column(name = "ended_at")
    public Instant endedAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "source_ip")
    public String sourceIp;

    @Column(name = "session_token", unique = true, nullable = false)
    public String sessionToken;

    @Column(nullable = false)
    public boolean active = true;
}
