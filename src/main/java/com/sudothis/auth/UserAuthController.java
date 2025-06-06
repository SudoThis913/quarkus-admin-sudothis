// File: src/main/java/com/sudothis/auth/UserAuthController.java

package com.sudothis.auth;

import com.sudothis.model.User;
import com.sudothis.service.UserService;
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
    public Response login(Credentials creds, @Context UriInfo uriInfo) {
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

        redis.setex(attemptsKey, 1, "0"); // clears attempt counter quickly

        String sessionId = UUID.randomUUID().toString();
        String sessionKey = "session:" + sessionId;
        String sessionData = String.format("{\"user\":\"%s\",\"created\":\"%s\"}", user.getUsername(), Instant.now().toString());

        redis.setex(sessionKey, sessionTimeout, sessionData);

        NewCookie sessionCookie = new NewCookie.Builder("sudothis_session")
            .value(sessionId)
            .path("/")
            .domain(null)
            .comment("Session cookie")
            .maxAge(sessionTimeout)
            .secure(true)
            .httpOnly(true)
            .build();

        return Response.ok().cookie(sessionCookie).build();
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

        String sessionKey = "session:" + sessionId;
        String sessionData = redis.get(sessionKey);

        if (sessionData == null || !sessionData.contains("\"user\":")) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String username = sessionData.replaceAll(".*\\\"user\\\":\\\"(.*?)\\\".*", "$1");

        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        redis.setex(sessionKey, sessionTimeout, sessionData); // refresh TTL safely

        return Response.ok(userOpt.get()).build();
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam("sudothis_session") String sessionId) {
        if (sessionId != null) {
            String sessionKey = "session:" + sessionId;
            redis.setex(sessionKey, 1, ""); // expires quickly
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/refresh")
    public Response refresh(@CookieParam("sudothis_session") String oldSessionId) {
        if (oldSessionId == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String oldSessionKey = "session:" + oldSessionId;
        String sessionData = redis.get(oldSessionKey);

        if (sessionData == null || !sessionData.contains("\"user\":")) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String username = sessionData.replaceAll(".*\\\"user\\\":\\\"(.*?)\\\".*", "$1");

        Optional<User> userOpt = userService.findByUsername(username);
        if (userOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        redis.setex(oldSessionKey, 1, "");

        String newSessionId = UUID.randomUUID().toString();
        String newSessionKey = "session:" + newSessionId;
        String newSessionData = String.format("{\"user\":\"%s\",\"created\":\"%s\"}", username, Instant.now().toString());

        redis.setex(newSessionKey, sessionTimeout, newSessionData);

        NewCookie sessionCookie = new NewCookie.Builder("sudothis_session")
            .value(newSessionId)
            .path("/")
            .domain(null)
            .comment("Refreshed session cookie")
            .maxAge(sessionTimeout)
            .secure(true)
            .httpOnly(true)
            .build();

        return Response.ok().cookie(sessionCookie).build();
    }

    public static class Credentials {
        public String username;
        public char[] password;
    }
}
