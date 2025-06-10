// path: src/main/java/com/sudothis/model/SiteAdmin.java
package com.sudothis.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "SITE_ADMIN")
public class SiteAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @OneToOne
    @JoinColumn(name = "USER_ID", nullable = false, unique = true)
    public AppUser user;

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
