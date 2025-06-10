// path: src/main/java/com/sudothis/service/UserService.java
package com.sudothis.service;

import com.sudothis.model.AppUser;
import com.sudothis.model.AppUser.UserType;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

import java.util.Arrays;
import java.util.Optional;

@ApplicationScoped
public class UserService implements PanacheRepository<AppUser> {

    private static final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

    @Transactional
    public AppUser registerUser(char[] rawPassword, String username, String email, UserType userType) {
        try {
            String hashed = hashPassword(rawPassword);
            AppUser user = new AppUser();
            user.username = username;
            user.email = email;
            user.passwordHash = hashed;
            user.userType = userType;
            user.enabled = false; // will be verified later
            user.persist();
            return user;
        } finally {
            Arrays.fill(rawPassword, '\0');
        }
    }

    public String hashPassword(char[] rawPassword) {
        try {
            return argon2.hash(3, 1 << 13, 1, rawPassword); // 3 iterations, 8MB, 1 thread
        } finally {
            Arrays.fill(rawPassword, '\0');
        }
    }

    public boolean verifyPassword(char[] rawPassword, String hashed) {
        try {
            return argon2.verify(hashed, rawPassword);
        } finally {
            Arrays.fill(rawPassword, '\0');
        }
    }

    public Optional<AppUser> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }

    public Optional<AppUser> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public Optional<AppUser> findByIdOptional(Long id) {
    return find("id", id).firstResultOptional();
}

}
