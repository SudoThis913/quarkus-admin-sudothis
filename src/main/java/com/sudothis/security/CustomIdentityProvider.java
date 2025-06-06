// File: src/main/java/com/sudothis/security/CustomIdentityProvider.java

package com.sudothis.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import com.sudothis.model.User;
import com.sudothis.service.UserService;

import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class CustomIdentityProvider implements SecurityIdentityAugmentor {

    private static final Logger LOG = Logger.getLogger(CustomIdentityProvider.class);

    @Inject
    UserService userService;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        return null;
        /*return Uni.createFrom().deferred(() -> {
            String username = identity.getPrincipal().getName();
            Optional<User> optionalUser = userService.findByUsername(username);

            if (optionalUser.isEmpty()) {
                LOG.warnf("No user record found in database for: %s", username);
                return identity;
            }

            User user = optionalUser.get();
            Set<String> roles = new HashSet<>();

            if (Boolean.TRUE.equals(user.getSiteAdmin())) {
                roles.add("SITE_ADMIN");
            }
            if (Boolean.TRUE.equals(user.getOrgAdmin())) {
                roles.add("ORG_ADMIN");
            }

            if (roles.isEmpty()) {
                return identity;
            }

            return SecurityIdentity.builder()
                    .principal(identity.getPrincipal())
                    .addRoles(roles)
                    .build();
        });
        */
    } 
}

/*
 * On any protected endpoint use:
 * 
 * @RolesAllowed("SITE_ADMIN")
 * 
 * @GET
 * 
 * @Path("/secure")
 * public String secureEndpoint() {
 * return "You are authenticated!";
 * }
 * 
 * 
 * 
 */