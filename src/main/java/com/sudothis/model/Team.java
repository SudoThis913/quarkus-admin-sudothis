// src/main/java/com/sudothis/model/Team.java

package com.sudothis.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import jakarta.persistence.*;

@Entity
public class Team extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "org_id", referencedColumnName = "id")
    public Org org;
}
