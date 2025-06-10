// path: src/main/java/com/sudothis/model/AppUser.java
package com.sudothis.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
//import io.quarkus.security.jpa.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "APP_USER")
public class AppUser extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "USER_ENABLED", nullable = false)
    public boolean enabled = true;

    @Column(nullable = false, unique = true)
    public String username;

    @Column(nullable = false, unique = true)
    public String email;

    @ManyToOne
    @JoinColumn(name = "ORG_ID", nullable = false)
    public Org org;

    @ManyToOne
    @JoinColumn(name = "TEAM_ID", nullable = false)
    public Team team;

    @Column(name = "API_KEY")
    public String apiKey;

    @Password
    @Column(name = "PASSWORD_HASH", nullable = false)
    public String passwordHash;

    @Column(name = "LAST_LOGGED_IN")
    public Instant lastLoggedIn;

    @Roles
    @Column(name = "USER_TYPE", nullable = false)
    @Enumerated(EnumType.STRING)
    public UserType userType;

    @Column(name = "SESSION_ID", unique = true)
    public String sessionId;

    @Column(name = "SESSION_EXPIRES")
    public Instant sessionExpires;

    @Column(name = "SESSION_IPV4")
    public String sessionIpv4;

    @Column(name = "CSRF_TOKEN")
    public String csrfToken;

    @Column(name = "USER_AGENT_HASH")
    public String userAgentHash;

    @Column(name = "DELETE_DATE")
    public Instant deleteDate;

    @Column(name = "CREATED", nullable = false, updatable = false)
    public Instant created = Instant.now();

    @Column(name = "UPDATED", nullable = false)
    public Instant updated = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updated = Instant.now();
    }

    public enum UserType {
        SITE_ADMIN, SITE_SUPPORT, USER
    }
}
