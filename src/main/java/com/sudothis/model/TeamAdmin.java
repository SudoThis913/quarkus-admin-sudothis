// path: src/main/java/com/sudothis/model/TeamAdmin.java
package com.sudothis.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "TEAM_ADMIN")
public class TeamAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    public AppUser user;

    @ManyToOne
    @JoinColumn(name = "TEAM_ID", nullable = false)
    public Team team;

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(nullable = false, updatable = false)
    public Instant created = Instant.now();

    @Column(nullable = false)
    public Instant updated = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updated = Instant.now();
    }
}
