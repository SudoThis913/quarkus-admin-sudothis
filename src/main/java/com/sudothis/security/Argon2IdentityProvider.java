// src/main/java/com/sudothis/security/Argon2IdentityProvider.java

package com.sudothis.security;

import com.sudothis.model.AppUser;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Arrays;

/**
 * IdentityProvider authenticating {@link UsernamePasswordAuthenticationRequest}
 * against Argon2‑hashed passwords.
 */
@ApplicationScoped
public class Argon2IdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

    private static final Logger LOG = Logger.getLogger(Argon2IdentityProvider.class);

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        String username = request.getUsername();
        char[] rawPassword = request.getPassword().getPassword();

                io.quarkus.security.runtime.QuarkusSecurityIdentity identity = null;
        try {
            AppUser user = AppUser.find("username", username).firstResult();
            if (user == null || !user.active) {
                LOG.debugf("User '%s' not found or inactive", username);
            } else if (Argon2PasswordProvider.verify(rawPassword, user.passwordHash)) {
                var builder = io.quarkus.security.runtime.QuarkusSecurityIdentity.builder();
                builder.setPrincipal(() -> username);
                for (String role : user.roles.split(",")) {
                    builder.addRole(role.trim());
                }
                builder.addCredential(new PasswordCredential(new char[0]));
                identity = builder.build();
            } else {
                LOG.debugf("Invalid credentials for '%s'", username);
            }
        } finally {
            Arrays.fill(rawPassword, '\0' );
        }

        return Uni.createFrom().item(identity); // null -> auth failure handled by Quarkus // null -> auth failure handled by Quarkus
    }
}
