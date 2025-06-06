// src/main/java/com/sudothis/model/User.java

package com.sudothis.model;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "USER")
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "USERNAME", nullable = false, unique = true)
    private String username;

    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;

    @Column(name = "API_KEY")
    private String apiKey;

    @Column(name = "PASSWORD_HASH", nullable = false)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORG_ID", nullable = false)
    private int orgID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TEAM_ID", nullable = false)
    private int teamID;

    @Column(name = "USER_TYPE", nullable = false)
    private String userType; //Remeber to enum this at some point, remove magic strings from get methods

    public int getId() {
        return id;
    }

    public void setId(int id) {
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

    public int getOrgID() {
        return orgID;
    }
     
    public void setOrgID(int in_orgID) {
        this.orgID = in_orgID;
    }

    public int getTeamID() {
        return teamID;
    }

    public void setTeamID(int in_team) {
        this.teamID = in_team;
    }

    public boolean getSiteAdmin() {
        return userType == "SITE_ADMIN";
    }

    public boolean getOrgAdmin() {
        return userType == "ORG_ADMIN";
    }
}
