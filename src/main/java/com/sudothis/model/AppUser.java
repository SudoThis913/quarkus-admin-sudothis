// src/main/java/com/sudothis/model/AppUser.java
package com.sudothis.model;

import com.sudothis.security.Argon2PasswordProvider;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.PasswordType;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import java.time.Instant;
import java.util.Arrays;

/**
 * Panache entity representing an application user. Relies on PanacheEntity's builtin `id` field.
 */
@Entity
@Table(name = "APP_USER")
@UserDefinition
public class AppUser extends PanacheEntity {

    // ---------------------------------------------------------------------
    // Columns (PanacheEntity already provides `id` primary key)
    // ---------------------------------------------------------------------

    @Username
    @Column(name = "USERNAME", unique = true, nullable = false)
    public String username;

    @Email
    @Column(name = "EMAIL", unique = true, nullable = false)
    public String email;

    @Column(name = "EMAIL_VERIFIED")
    public boolean emailVerified = false;

    @Password(value = PasswordType.CUSTOM, provider = Argon2PasswordProvider.class)
    @Column(name = "PASSWORD_HASH", nullable = false)
    public String passwordHash;

    @Roles
    @Column(name = "ROLES", nullable = false)
    public String roles;

    @Column(name = "CREATED_AT", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "IS_ACTIVE")
    public boolean active = true;

    @Column(name= "VERIFIED")
    public boolean verified = false;

    // ---------------------------------------------------------------------
    // Factory helper
    // ---------------------------------------------------------------------
    public static AppUser create(String username, String email, char[] rawPassword, String roles) {
        AppUser user = new AppUser();
        user.username = username;
        user.email = email;
        user.roles = roles;

        user.passwordHash = Argon2PasswordProvider.hashPassword(rawPassword);
        Arrays.fill(rawPassword, '\0');

        user.persist();
        return user;
    }

    // ---------------------------------------------------------------------
    // Accessors (used by REST layer)
    // ---------------------------------------------------------------------
    public String getEmail()             { return email; }
    public void   setEmail(String email) { this.email = email; }
    public String getRoles()             { return roles; }
    public void   setRoles(String roles) { this.roles = roles; }

    // ---------------------------------------------------------------------
    // Delete helpers
    // ---------------------------------------------------------------------
    public static boolean deleteByUsername(String username) {
        return delete("username", username) > 0;
    }

    public static boolean deleteById(Long userId) {
        return delete("id", userId) > 0;
    }
}
