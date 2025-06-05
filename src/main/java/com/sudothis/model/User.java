package com.sudothis.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class User extends PanacheEntity {

    @Column(unique = true)
    public String username;

    public String passwordHash;

    public boolean isAdmin;
}
