package com.sudothis.auth;

import com.sudothis.model.User;
import com.sudothis.service.UserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

@ApplicationScoped
public class SessionValidator {

    @Inject
    SessionManager sessionManager;

    @Inject
    UserService userService;

    /**
     * Validate session token and contextual fingerprint.
     * @throws NotAuthorizedException if validation fails.
     */
    public User validate(String sessionToken, HttpServletRequest request, HttpHeaders headers) {
        Optional<User> userOpt = sessionManager.getUserFromSession(sessionToken);

        if (userOpt.isEmpty()) {
            throw new NotAuthorizedException("Invalid or expired session.");
        }

        User user = userOpt.get();

        if (user.getDeleteDate() != null) {
            throw new NotAuthorizedException("User has been deleted.");
        }

        // Check IP prefix match
        String clientIp = extractClientIp(request);
        if (!ipPrefixMatch(clientIp, user.getSessionIp())) {
            throw new NotAuthorizedException("IP mismatch.");
        }

        // Check user-agent hash match
        String userAgent = headers.getHeaderString("User-Agent");
        if (userAgent != null && !hash(userAgent).equals(user.getUserAgentHash())) {
            throw new NotAuthorizedException("User-Agent mismatch.");
        }

        return user;
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private boolean ipPrefixMatch(String ipA, String ipB) {
        if (ipA == null || ipB == null) return false;
        int prefixLength = 3; // compare first 3 octets (e.g., 192.168.1)
        String[] a = ipA.split("\\.");
        String[] b = ipB.split("\\.");
        if (a.length < prefixLength || b.length < prefixLength) return false;

        for (int i = 0; i < prefixLength; i++) {
            if (!a[i].equals(b[i])) return false;
        }
        return true;
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
