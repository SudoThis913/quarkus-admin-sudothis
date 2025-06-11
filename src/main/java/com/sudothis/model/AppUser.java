package com.sudothis.model;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "APP_USER")
public class AppUser {

    // Legacy enum to keep existing controllers compiling
    public enum UserType { USER, TEAM_ADMIN, ORG_ADMIN, SITE_ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    public Long id;

    @Column(name = "USERNAME", nullable = false, unique = true, length = 64)
    public String username;

    @Column(name = "EMAIL",   nullable = false, unique = true, length = 128)
    public String email;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 256)
    public String passwordHash;

    // Compatibility shim — can be dropped after UI rewrite
    @Column(name = "USER_ENABLED")
    public boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "USER_TYPE", length = 32)
    public UserType userType = UserType.USER;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "USER_ROLE", joinColumns = @JoinColumn(name = "USER_ID"))
    @Column(name = "ROLE", length = 32)
    public Set<String> roles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<PersistentSession> sessions = new ArrayList<>();

    public void addSession(PersistentSession ps) {
        sessions.add(ps);
        ps.user = this;
    }
}