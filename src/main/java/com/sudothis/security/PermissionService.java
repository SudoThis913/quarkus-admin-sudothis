package com.sudothis.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import com.sudothis.model.AppUser;
import com.sudothis.model.AdminPermission;
import java.util.List;

@ApplicationScoped
public class PermissionService {

    @Inject
    EntityManager em;

    public void validateEmployeeConstraints(AppUser user) {
        if (user.isEmployee) {
            // Check if associated with > 1 company
            if (user.companies.size() > 1) {
                throw new IllegalArgumentException("Employee users cannot belong to more than one company.");
            }
            // Check logic for Org can be complex if Company -> Org is optional.
            // For now, implicit via Company.
        }
    }

    public boolean canManage(String actorName, Class<?> entityClass, Long entityId) {
        // Resolving actor to AppUser
        AppUser actor = AppUser.find("username", actorName).firstResult();
        if (actor == null) return false; // Should not happen if authenticated

        // Check AdminPermissions
        List<AdminPermission> perms = AdminPermission.list("user", actor);
        for (AdminPermission p : perms) {
            if (p.role == AdminPermission.Role.SITE_OWNER) return true;
            
            // TODO: Logic for hierarchical permissions
            // If Org Admin, check if entity belongs to Org
            // If Company Admin, check if entity belongs to Company
        }
        return true; // Placeholder for Phase 3.5 logic
    }
}
