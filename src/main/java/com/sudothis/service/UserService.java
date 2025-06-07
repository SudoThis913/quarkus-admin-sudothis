// File: src/main/java/com/sudothis/service/UserService.java

package com.sudothis.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import com.sudothis.model.User;
import com.sudothis.repository.UserRepository;
import com.sudothis.auth.AccessControl;
import com.sudothis.auth.AccessControl.PermissionType;
import at.favre.lib.crypto.bcrypt.BCrypt;

import java.util.Arrays;
import java.util.Optional;

@ApplicationScoped
public class UserService {

    @Inject
    UserRepository userRepository;

    @Inject
    AccessControl accessControl;

    private static final int BCRYPT_WORK_FACTOR = 12;

    @Transactional
    public User registerUser(char[] rawPassword, String username, String email) {
        try {
            String hashed = hashPassword(rawPassword);
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPasswordHash(hashed);
            userRepository.persist(user);
            return user;
        } finally {
            Arrays.fill(rawPassword, '\0');
        }
    }

    @Transactional
    public boolean updateUser(User actingUser, long targetUserId, String newEmail) {
        User targetUser = userRepository.findById(targetUserId);
        if (targetUser == null) return false;

        if (!accessControl.hasPermission(actingUser, targetUser, PermissionType.RESET_PASSWORD)) {
            return false;
        }

        targetUser.setEmail(newEmail);
        return true;
    }

    @Transactional
    public boolean deleteUser(User actingUser, long targetUserId) {
        User targetUser = userRepository.findById(targetUserId);
        if (targetUser == null) return false;

        if (!accessControl.hasPermission(actingUser, targetUser, PermissionType.RESET_PASSWORD)) {
            return false;
        }

        userRepository.delete(targetUser);
        return true;
    }

    public String hashPassword(char[] rawPassword) {
        return BCrypt.withDefaults().hashToString(BCRYPT_WORK_FACTOR, rawPassword);
    }

    public boolean verifyPassword(char[] rawPassword, String hashed) {
        return BCrypt.verifyer().verify(rawPassword, hashed).verified;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.find("username", username).firstResultOptional();
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.find("email", email).firstResultOptional();
    }

    public Optional<User> findById(int id) {
        return Optional.ofNullable(userRepository.findById((long) id));
    }

    public Optional<User> findBySessionToken(String token) {
        return userRepository.find("sessionToken", token).firstResultOptional();
    }

    @Transactional
    public void updateSessionInfo(User user) {
        userRepository.persist(user);
    }

    @Transactional
    public void clearSessionToken(String token) {
        findBySessionToken(token).ifPresent(user -> {
            user.setSessionToken(null);
            user.setSessionIp(null);
        });
    }
}
