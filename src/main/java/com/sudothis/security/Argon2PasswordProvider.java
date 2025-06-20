// src/main/java/com/sudothis/security/Argon2PasswordProvider.java

package com.sudothis.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import io.quarkus.security.jpa.PasswordProvider;
import jakarta.enterprise.context.ApplicationScoped;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.interfaces.ClearPassword;

import java.util.Arrays;

/**
 * Quarkus {@link PasswordProvider} exposing an Argon2id hash.
 * <p>
 * Elytron lacks a dedicated Argon2 {@link Password} implementation, so we wrap the stored
 * hash in a {@link ClearPassword}. A custom IdentityProvider will call
 * {@link #verify(char[], String)} to compare credentials at login time.
 */
@ApplicationScoped
public class Argon2PasswordProvider implements PasswordProvider {

    private static final Argon2 ARGON2 = Argon2Factory.create();

    // ------------------------------------------------------------------
    // PasswordProvider contract
    // ------------------------------------------------------------------

    @Override
    public Password getPassword(String passwordInDatabase) {
        return ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, passwordInDatabase.toCharArray());
    }

    // ------------------------------------------------------------------
    // Static helpers
    // ------------------------------------------------------------------

    /** Hashes <code>rawPassword</code> and wipes the char array. */
    public static String hashPassword(char[] rawPassword) {
        try {
            return ARGON2.hash(3, 1 << 16, 1, rawPassword);
        } finally {
            Arrays.fill(rawPassword, '\0');
        }
    }

    /** Verifies <code>rawPassword</code> against an Argon2 hash. */
    public static boolean verify(char[] rawPassword, String argon2Hash) {
        try {
            return ARGON2.verify(argon2Hash, rawPassword);
        } finally {
            Arrays.fill(rawPassword, '\0');
        }
    }
}
