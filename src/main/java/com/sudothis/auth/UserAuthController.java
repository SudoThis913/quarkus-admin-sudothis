// File: src/main/java/com/sudothis/auth/UserAuthController.java

package com.sudothis.auth;

import com.sudothis.model.User;
import com.sudothis.service.UserService;
import com.sudothis.auth.SessionManager;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserAuthController {

    @Inject
    UserService userService;

    @Inject
    SessionManager sessionManager;

    @Inject
    RedisDataSource redisDS;

    @ConfigProperty(name = "session.timeout", defaultValue = "604800")
    int sessionTimeout;

    @ConfigProperty(name = "auth.maxAttempts", defaultValue = "10")
    int maxAttempts;

    @ConfigProperty(name = "auth.blockDuration", defaultValue = "600") // seconds
    int blockDuration;

    private ValueCommands<String, String> redis;

    @PostConstruct
    void init() {
        this.redis = redisDS.value(String.class, String.class);
    }

    @POST
    @Path("/login")
    public Response login(Credentials creds, @Context UriInfo uriInfo, @Context HttpHeaders headers) {
        String attemptsKey = "login_attempts:" + creds.username;
        String blockKey = "login_blocked:" + creds.username;

        if (redis.get(blockKey) != null) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS).entity("Too many failed attempts. Try again later.").build();
        }

        Optional<User> userOpt = userService.findByUsername(creds.username);
        if (userOpt.isEmpty()) {
            redis.setex(attemptsKey, blockDuration, String.valueOf(incrementAttempts(attemptsKey)));
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        User user = userOpt.get();

        char[] passwordBuffer = creds.password;
        char[] storedHash = user.getPasswordHash().toCharArray();
        boolean passwordMatch = BCrypt.verifyer()
                .verify(passwordBuffer, storedHash)
                .verified;
        Arrays.fill(passwordBuffer, '\0');
        Arrays.fill(storedHash, '\0');
        creds.password = null;

        if (!passwordMatch) {
            if (incrementAttempts(attemptsKey) >= maxAttempts) {
                redis.setex(blockKey, blockDuration, "1");
            }
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        redis.setex(attemptsKey, 1, "0");

        String clientIp = headers.getHeaderString("X-Forwarded-For");
        if (clientIp == null) {
            clientIp = headers.getRequestHeader("Host").stream().findFirst().orElse("unknown");
        }

        user.setSessionIp(clientIp);
        String sessionId = sessionManager.createSession(user);

        // Generate CSRF token and store in Redis
        String csrfToken = UUID.randomUUID().toString();
        redis.setex("csrf:" + sessionId, sessionTimeout, csrfToken);

        NewCookie sessionCookie = new NewCookie.Builder("sudothis_session")
            .value(sessionId)
            .path("/")
            .domain(null)
            .comment("Session cookie")
            .maxAge(sessionTimeout)
            .secure(true)
            .httpOnly(true)
            .build();

        NewCookie csrfCookie = new NewCookie.Builder("csrf_token")
            .value(csrfToken)
            .path("/")
            .domain(null)
            .comment("CSRF token")
            .maxAge(sessionTimeout)
            .secure(true)
            .httpOnly(false) // Accessible via JS for XHR use
            .build();

        return Response.ok().cookie(sessionCookie).cookie(csrfCookie).build();
    }

    private int incrementAttempts(String key) {
        String val = redis.get(key);
        int attempts = val == null ? 1 : Integer.parseInt(val) + 1;
        redis.setex(key, blockDuration, String.valueOf(attempts));
        return attempts;
    }

    @GET
    @Path("/whoami")
    public Response whoami(@CookieParam("sudothis_session") String sessionId) {
        if (sessionId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<User> userOpt = sessionManager.getUserFromSession(sessionId);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        return Response.ok(userOpt.get()).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam("sudothis_session") String sessionId) {
        if (sessionId != null) {
            sessionManager.invalidateSession(sessionId);
            redis.setex("csrf:" + sessionId, 1, ""); // purge CSRF
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/refresh")
    public Response refresh(@CookieParam("sudothis_session") String oldSessionId) {
        if (oldSessionId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Optional<User> userOpt = sessionManager.getUserFromSession(oldSessionId);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        sessionManager.invalidateSession(oldSessionId);
        redis.setex("csrf:" + oldSessionId, 1, "");

        String newSessionId = sessionManager.createSession(userOpt.get());
        String csrfToken = UUID.randomUUID().toString();
        redis.setex("csrf:" + newSessionId, sessionTimeout, csrfToken);

        NewCookie sessionCookie = new NewCookie.Builder("sudothis_session")
            .value(newSessionId)
            .path("/")
            .domain(null)
            .comment("Refreshed session cookie")
            .maxAge(sessionTimeout)
            .secure(true)
            .httpOnly(true)
            .build();

        NewCookie csrfCookie = new NewCookie.Builder("csrf_token")
            .value(csrfToken)
            .path("/")
            .domain(null)
            .comment("CSRF token")
            .maxAge(sessionTimeout)
            .secure(true)
            .httpOnly(false)
            .build();

        return Response.ok().cookie(sessionCookie).cookie(csrfCookie).build();
    }

    public static class Credentials {
        public String username;
        public char[] password;
    }
}
