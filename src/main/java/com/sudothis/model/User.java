// File: src/main/java/com/sudothis/model/User.java

package com.sudothis.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "APP_USER")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "USER_ENABLED")
    private Boolean enabled;

    @Column(name = "USERNAME", nullable = false, unique = true)
    private String username;

    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;

    @ManyToOne
    @JoinColumn(name = "ORG_ID", insertable = false, updatable = false)
    private Org org;

    @Column(name = "ORG_ID", nullable = false)
    private int orgID;

    @Column(name = "TEAM_ID", nullable = false)
    private int teamID;

    @Column(name = "API_KEY")
    private String apiKey;

    @Column(name = "PASSWORD_HASH")
    private String passwordHash;

    @Column(name = "LAST_LOGGED_IN")
    private LocalDateTime lastLoggedIn;

    @Column(name = "USER_TYPE", nullable = false)
    @Enumerated(EnumType.STRING)
    private UserType userType;

    @Column(name = "SESSION_ID", unique = true)
    private String sessionToken;

    @Column(name = "SESSION_EXPIRES")
    private Instant sessionExpires;

    @Column(name = "SESSION_IPV4")
    private String sessionIp;

    @Column(name = "CSRF_TOKEN")
    private String csrfToken;

    @Column(name = "USER_AGENT_HASH")
    private String userAgentHash;

    @Column(name = "CREATED", updatable = false)
    private LocalDateTime created;

    @Column(name = "UPDATED")
    private LocalDateTime updated;

    @Column(name = "DELETE_DATE")
private LocalDateTime deleteDate;

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    public int getOrgID() {
        return orgID;
    }

    public void setOrgID(int orgID) {
        this.orgID = orgID;
    }

    public int getTeamID() {
        return teamID;
    }

    public void setTeamID(int teamID) {
        this.teamID = teamID;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getLastLoggedIn() {
        return lastLoggedIn;
    }

    public void setLastLoggedIn(LocalDateTime lastLoggedIn) {
        this.lastLoggedIn = lastLoggedIn;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public Instant getSessionExpires() {
        return sessionExpires;
    }

    public void setSessionExpires(Instant sessionExpires) {
        this.sessionExpires = sessionExpires;
    }

    public String getSessionIp() {
        return sessionIp;
    }

    public void setSessionIp(String sessionIp) {
        this.sessionIp = sessionIp;
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    public void setCsrfToken(String csrfToken) {
        this.csrfToken = csrfToken;
    }

    public String getUserAgentHash() {
        return userAgentHash;
    }

    public void setUserAgentHash(String userAgentHash) {
        this.userAgentHash = userAgentHash;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }

    public enum UserType {
        SITE_ADMIN,
        SITE_SUPPORT,
        USER
    }

    public LocalDateTime getDeleteDate() {
    return deleteDate;
}
}
