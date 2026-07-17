package com.sudothis.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Self-service identity operations against the Keycloak Admin REST API, using
 * the sudothis-backend service account (client credentials).
 *
 * When no server URL is configured (tests, %h2 dev mode) the service reports
 * itself unavailable and callers fall back to local-only behavior.
 *
 * All methods act on a single Keycloak user id — callers are responsible for
 * only ever passing the *caller's own* sub (self-service, never management).
 */
@ApplicationScoped
public class KeycloakAdminService {

    private static final Logger LOG = Logger.getLogger(KeycloakAdminService.class);

    @ConfigProperty(name = "sudothis.kc-admin.server-url")
    Optional<String> serverUrl;

    @ConfigProperty(name = "sudothis.kc-admin.realm", defaultValue = "sudothis")
    String realm;

    @ConfigProperty(name = "sudothis.kc-admin.client-id", defaultValue = "sudothis-backend")
    String clientId;

    @ConfigProperty(name = "sudothis.kc-admin.client-secret")
    Optional<String> clientSecret;

    /** Public client used to verify the current password via a direct grant. */
    @ConfigProperty(name = "quarkus.oidc.client-id", defaultValue = "sudothis-app")
    String appClientId;

    @Inject
    ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    public boolean isConfigured() {
        return serverUrl.filter(s -> !s.isBlank()).isPresent()
            && clientSecret.filter(s -> !s.isBlank()).isPresent();
    }

    /** Updates first/last name and email on the Keycloak user. */
    public void updateProfile(String userId, String firstName, String lastName, String email) {
        Map<String, Object> body = Map.of(
            "firstName", firstName,
            "lastName", lastName,
            "email", email,
            "emailVerified", true
        );
        adminRequest("PUT", "/users/" + userId, body, 204);
    }

    /**
     * Verifies the caller's current password by attempting a resource-owner
     * password grant against the public app client. Keycloak's admin
     * reset-password endpoint does not check the old credential, so this is
     * the guard that stops a hijacked session from silently rotating the
     * password.
     */
    public boolean verifyPassword(String username, String currentPassword) {
        String form = "grant_type=password"
            + "&client_id=" + url(appClientId)
            + "&username=" + url(username)
            + "&password=" + url(currentPassword)
            + "&scope=openid";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(base() + "/realms/" + realm + "/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (Exception e) {
            LOG.errorf(e, "Password verification call to Keycloak failed");
            return false;
        }
    }

    public void resetPassword(String userId, String newPassword) {
        Map<String, Object> cred = Map.of(
            "type", "password",
            "value", newPassword,
            "temporary", false
        );
        adminRequest("PUT", "/users/" + userId + "/reset-password", cred, 204);
    }

    public void deleteUser(String userId) {
        adminRequest("DELETE", "/users/" + userId, null, 204);
    }

    /** Fetches the Keycloak user representation (firstName, lastName, email). */
    public JsonNode getUser(String userId) {
        return adminRequest("GET", "/users/" + userId, null, 200);
    }

    /**
     * Resolves the Keycloak user UUID by exact username. Needed because
     * code-flow sessions don't expose the sub claim as an identity attribute,
     * so local AppUser.keycloakSub may hold the username rather than the UUID.
     */
    public String resolveUserId(String username) {
        JsonNode users = adminRequest("GET",
            "/users?exact=true&username=" + url(username), null, 200);
        if (users == null || !users.isArray() || users.isEmpty()) {
            throw new IllegalStateException("No Keycloak user found for username " + username);
        }
        return users.get(0).get("id").asText();
    }

    // ------------------------------------------------------------------

    private String base() {
        return serverUrl.orElseThrow(() ->
            new IllegalStateException("Keycloak admin service is not configured"));
    }

    private JsonNode adminRequest(String method, String path, Object body, int expectStatus) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(base() + "/admin/realms/" + realm + path))
                .header("Authorization", "Bearer " + serviceToken())
                .timeout(Duration.ofSeconds(15));
            if (body != null) {
                b.header("Content-Type", "application/json")
                 .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != expectStatus) {
                throw new IllegalStateException(
                    "Keycloak admin call " + method + " " + path + " returned " + res.statusCode());
            }
            return res.body() == null || res.body().isBlank() ? null : mapper.readTree(res.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak admin call failed: " + method + " " + path, e);
        }
    }

    private synchronized String serviceToken() throws Exception {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }
        String form = "grant_type=client_credentials"
            + "&client_id=" + url(clientId)
            + "&client_secret=" + url(clientSecret.orElse(""));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(base() + "/realms/" + realm + "/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Service account token request failed: " + res.statusCode());
        }
        JsonNode json = mapper.readTree(res.body());
        cachedToken = json.get("access_token").asText();
        // refresh 30s before expiry
        cachedTokenExpiry = Instant.now().plusSeconds(Math.max(30, json.path("expires_in").asLong(60) - 30));
        return cachedToken;
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
