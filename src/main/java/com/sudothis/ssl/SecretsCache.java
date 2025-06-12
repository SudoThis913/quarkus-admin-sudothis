package com.sudothis.ssl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SecretsCache {
    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    public String getSecret(String key) {
        return secrets.computeIfAbsent(key, k -> {
            // TODO: Pull from env var, file, or external vault in prod
            return System.getenv(k);
        });
    }

    public void preload(String key, String value) {
        secrets.putIfAbsent(key, value);
    }
}
