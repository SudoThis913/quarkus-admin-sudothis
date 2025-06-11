package com.sudothis.security;

import com.sudothis.model.AppUser;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

@ApplicationScoped
public class AccessControlService {

    public Set<String> rolesForUser(AppUser u) {
        HashSet<String> roles = new HashSet<>(u.roles);
        if (u.userType != null) roles.add(u.userType.name());
        return roles;
    }

    public boolean canEditUser(AppUser actor, AppUser target) {
        if (rolesForUser(actor).contains("SITE_ADMIN")) return true;
        return Objects.equals(actor.id, target.id);
    }
}