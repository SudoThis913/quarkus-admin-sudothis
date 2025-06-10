// path: src/main/java/com/sudothis/model/Team.java
package com.sudothis.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "TEAM")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Column(nullable = false)
    public String name;

    @ManyToOne
    @JoinColumn(name = "ORG_ID", nullable = false)
    public Org org;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL)
    public List<AppUser> users;
}
