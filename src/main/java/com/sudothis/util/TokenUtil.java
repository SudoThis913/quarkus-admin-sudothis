// File: src/main/java/com/sudothis/util/TokenUtil.java

package com.sudothis.util;

import java.security.SecureRandom;
import java.util.Base64;

public class TokenUtil {

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a secure random session token of the specified byte length.
     * Output is base64-url encoded and safe for transport.
     *
     * @param byteLength Number of random bytes (e.g., 32 for 256-bit token)
     * @return Secure base64url-encoded string
     */
    public static String generateSecureToken(int byteLength) {
        byte[] token = new byte[byteLength];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}
