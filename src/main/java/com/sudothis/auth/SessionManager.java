// File: src/main/java/com/sudothis/auth/SessionManager.java

package com.sudothis.auth;

import com.sudothis.model.User;
import com.sudothis.service.UserService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

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

    public String createSession(User user) {
        String token = UUID.randomUUID().toString();
        SetArgs setArgs = new SetArgs().ex(SESSION_TTL);
        valueCommands.set(getRedisKey(token), String.valueOf(user.getId()), setArgs);
        user.setSessionToken(token);
        userService.updateSessionInfo(user); // Persist token in DB for fallback
        return token;
    }

    public Optional<User> getUserFromSession(String token) {
        String redisKey = getRedisKey(token);
        String userId = valueCommands.get(redisKey);

        if (userId != null) {
            return userService.findById(Integer.parseInt(userId));
        } else {
            // Fallback to DB
            return userService.findBySessionToken(token);
        }
    }

    public void invalidateSession(String token) {
        SetArgs setArgs = new SetArgs().ex(Duration.ZERO);
        valueCommands.set(getRedisKey(token), "", setArgs);
        userService.clearSessionToken(token);
    }

    private String getRedisKey(String token) {
        return "session:" + token;
    }
}
