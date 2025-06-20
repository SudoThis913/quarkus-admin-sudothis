// src/main/java/com/sudothis/repository/AppUserRepository.java
package com.sudothis.repository;

import com.sudothis.model.AppUser;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Lightweight Panache repository wrapper so other layers (e.g. SessionFilter)
 * can inject a type‑safe DAO rather than using the static
 * <code>AppUser.findXXX()</code> helpers directly.
 */
@ApplicationScoped
public class AppUserRepository implements PanacheRepository<AppUser> {
    // Panache supplies all the usual CRUD helpers (findById, find, listAll, etc.).
    // Add custom query methods here if and when you need them.
}
