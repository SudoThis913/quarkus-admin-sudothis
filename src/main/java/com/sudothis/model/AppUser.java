

package com.sudothis.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

@Entity
@Table(name = "APP_USER")
@UserDefinition
public class AppUser extends PanacheEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Username
    @Column(name = "USERNAME", unique = true, nullable = false)
    public String username;

    @Column(name = "EMAIL", unique = true, nullable = false)
    public String email;

    @Column(name = "EMAIL_VERIFIED")
    public boolean emailVerified = false;

    @Password
    @Column(name = "PASSWORD_HASH", nullable = false)
    public String password;

    @Roles
    @Column(name = "ROLES", nullable = false)
    public String role; // Comma-separated e.g. "USER,SITE_ADMIN"

    @Column(name = "CREATED_AT", nullable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "IS_ACTIVE")
    public boolean active = true;

    private static final Argon2 ARGON2 = Argon2Factory.create();

    /**
     * Adds a new user in the database
     *
     * @param username the user name
     * @param rawPassword the unencrypted password (as a char[])
     * @param role     the comma-separated roles
     */
    public static void add(String username, char[] rawPassword, String role) {
        AppUser user = new AppUser();
        user.username = username;
        try {
            user.password = ARGON2.hash(3, 65536, 1, rawPassword);
        } finally {
            java.util.Arrays.fill(rawPassword, '\0');
        }
        user.role = role;
        user.persist();
    }

    // ------------Getters / Setters --------------
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean verified) {
        this.emailVerified = verified;
    }

    public String getPassword() {
        return password;
    }

    /**
     * WARNING: This method sets a raw password string. Hash externally before use or use add().
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    // ------------Delete methods--------------
    public static boolean deleteByUsername(String username) {
        return delete("username", username) > 0;
    }

    public static boolean deleteById(Long userId) {
        return delete("id", userId) > 0;
    }

    public String hashPassword (char[] rawPassword)
    {
        try {
            String hashedPassword = ARGON2.hash(3, 65536, 1, rawPassword);
            return hashedPassword;
        } finally {
            java.util.Arrays.fill(rawPassword, '\0');
        }
        
    }

    /**
     * Utility method to verify password
     *
     * @param rawPassword the unencrypted password (as char[])
     * @return true if matches stored hash
     */
    public boolean checkPassword(char[] rawPassword) {
        try {
            return ARGON2.verify(this.password, rawPassword);
        } finally {
            java.util.Arrays.fill(rawPassword, '\0');
        }
    }
}
