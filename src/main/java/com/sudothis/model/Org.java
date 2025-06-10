// path: src/main/java/com/sudothis/model/Org.java
package com.sudothis.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "ORG")
public class Org {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Column(nullable = false)
    public boolean enabled;

    @Column(nullable = false, unique = true)
    public String name;

    @OneToMany(mappedBy = "org", cascade = CascadeType.ALL)
    public List<Team> teams;

    @OneToMany(mappedBy = "org", cascade = CascadeType.ALL)
    public List<AppUser> users;
}
