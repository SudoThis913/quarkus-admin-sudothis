// path: src/main/java/com/sudothis/model/OrgAdmin.java
package com.sudothis.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ORG_ADMIN")
public class OrgAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne
    @JoinColumn(name = "USER_ID", nullable = false)
    public AppUser user;

    @ManyToOne
    @JoinColumn(name = "ORG_ID", nullable = false)
    public Org org;

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
