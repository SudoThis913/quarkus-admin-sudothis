// File: src/main/java/com/sudothis/auth/SessionManager.java

package com.sudothis.auth;

import com.sudothis.model.User;
import com.sudothis.service.UserService;
import com.sudothis.util.TokenUtil; // ← added
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class SessionManager {

    private static final Duration SESSION_TTL = Duration.ofDays(3);

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    UserService userService;

    private ValueCommands<String, String> valueCommands;

    @jakarta.annotation.PostConstruct
    void init() {
        valueCommands = redisDataSource.value(String.class);
    }

    /**
     * Creates a new session with a secure token.
     * - Token written to Redis with TTL
     * - Session info stored in DB as fallback
     */
    public String createSession(User user) {
        String token = TokenUtil.generateSecureToken(32); // ← secure 256-bit session ID
        SetArgs setArgs = new SetArgs().ex(SESSION_TTL);
        valueCommands.set(getRedisKey(token), String.valueOf(user.getId()), setArgs);

        user.setSessionToken(token);
        user.setSessionExpires(Instant.now().plus(SESSION_TTL));
        userService.updateSessionInfo(user);

        return token;
    }

    /**
     * Retrieves user from session token:
     * - Primary: Redis
     * - Fallback: DB → rehydrate Redis
     */
    public Optional<User> getUserFromSession(String token) {
        String redisKey = getRedisKey(token);
        String userId = valueCommands.get(redisKey);

        if (userId != null) {
            return userService.findById(Integer.parseInt(userId));
        }

        Optional<User> userOpt = userService.findBySessionToken(token);
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            if (user.getSessionExpires() != null &&
                user.getSessionExpires().isAfter(Instant.now())) {

                SetArgs setArgs = new SetArgs().ex(SESSION_TTL);
                valueCommands.set(redisKey, String.valueOf(user.getId()), setArgs);
                return Optional.of(user);
            }

            userService.clearSessionToken(token);
        }

        return Optional.empty();
    }

    /**
     * Invalidates a session everywhere.
     */
    public void invalidateSession(String token) {
        SetArgs setArgs = new SetArgs().ex(Duration.ZERO);
        valueCommands.set(getRedisKey(token), "", setArgs);
        userService.clearSessionToken(token);
    }

    private String getRedisKey(String token) {
        return "session:" + token;
    }
}
