// path: src/main/java/com/sudothis/security/AccessControlService.java
package com.sudothis.security;

import com.sudothis.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
//import jakarta.transaction.Transactional;
//import java.util.Optional;

@ApplicationScoped
public class AccessControlService {

    @Inject
    EntityManager em;

    public boolean isSiteAdmin(AppUser user) {
        return user.userType == AppUser.UserType.SITE_ADMIN &&
               em.createQuery("SELECT COUNT(sa) FROM SiteAdmin sa WHERE sa.user.id = :uid AND sa.enabled = true", Long.class)
                 .setParameter("uid", user.id)
                 .getSingleResult() > 0;
    }

    public boolean isOrgAdmin(AppUser user, Integer orgId) {
        return em.createQuery("SELECT COUNT(oa) FROM OrgAdmin oa WHERE oa.user.id = :uid AND oa.org.id = :oid AND oa.enabled = true", Long.class)
                 .setParameter("uid", user.id)
                 .setParameter("oid", orgId)
                 .getSingleResult() > 0;
    }

    public boolean isTeamAdmin(AppUser user, Integer teamId) {
        return em.createQuery("SELECT COUNT(ta) FROM TeamAdmin ta WHERE ta.user.id = :uid AND ta.team.id = :tid AND ta.enabled = true", Long.class)
                 .setParameter("uid", user.id)
                 .setParameter("tid", teamId)
                 .getSingleResult() > 0;
    }

    public boolean canEditUser(AppUser actor, AppUser target) {
        if (isSiteAdmin(actor)) return true;
        if (actor.id.equals(target.id)) return true;
        if (actor.org != null && actor.org.id.equals(target.org.id)) {
            if (isOrgAdmin(actor, actor.org.id)) return true;
            if (actor.team != null && actor.team.id.equals(target.team.id) && isTeamAdmin(actor, actor.team.id)) return true;
        }
        return false;
    }
}
