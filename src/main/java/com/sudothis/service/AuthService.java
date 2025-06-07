package com.sudothis.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.sudothis.model.User;
import com.sudothis.service.UserService;
import at.favre.lib.crypto.bcrypt.BCrypt;

import java.util.Arrays;
import java.util.Optional;

@ApplicationScoped
public class AuthService {

    @Inject
    UserService userService;

    public Optional<User> authenticate(String username, char[] rawPassword) {
        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) return Optional.empty();

        boolean verified = false;
        try {
            verified = BCrypt.verifyer()
                .verify(rawPassword, userOpt.get().getPasswordHash().toCharArray())
                .verified;
        } finally {
            Arrays.fill(rawPassword, '\0');
        }

        return verified ? userOpt : Optional.empty();
    }
}
