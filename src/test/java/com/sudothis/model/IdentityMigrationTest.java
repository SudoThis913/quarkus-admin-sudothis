package com.sudothis.model;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pre-sub-claim deployments stored the username in AppUser.keycloakSub (the
 * code-flow fallback). ensureExists() must re-key such rows to the real OIDC
 * sub in place — preserving the row (and its permissions) — instead of
 * creating a duplicate user.
 */
@QuarkusTest
public class IdentityMigrationTest {

    @Test
    @TestTransaction
    void testLegacyUsernameKeyedRowIsMigratedToRealSub() {
        AppUser legacy = new AppUser();
        legacy.username = "legacy@example.com";
        legacy.keycloakSub = "legacy@example.com"; // fallback-keyed
        legacy.persist();
        Long originalId = legacy.id;

        AppUser resolved = AppUser.ensureExists(
            "11111111-2222-3333-4444-555555555555", "legacy@example.com", "legacy@example.com");

        assertEquals(originalId, resolved.id, "must re-key the existing row, not create a new one");
        assertEquals("11111111-2222-3333-4444-555555555555", resolved.keycloakSub);
        assertEquals(1, AppUser.count("username", "legacy@example.com"), "no duplicate row");
    }

    @Test
    @TestTransaction
    void testDifferentUserIsNotCaptured() {
        AppUser other = new AppUser();
        other.username = "other@example.com";
        other.keycloakSub = "aaaa-real-sub-of-other-user";
        other.persist();

        AppUser created = AppUser.ensureExists(
            "bbbb-new-sub", "other@example.com", "other@example.com");

        // keycloakSub of the existing row differs from the username, so it is
        // NOT a legacy fallback row — a distinct user must be created
        assertNotEquals(other.id, created.id);
        assertEquals("aaaa-real-sub-of-other-user",
            AppUser.<AppUser>findById(other.id).keycloakSub, "existing user untouched");
    }

    @Test
    @TestTransaction
    void testNoMigrationWhenSubEqualsUsername() {
        AppUser resolved = AppUser.ensureExists("same-value", "same-value", null);
        assertEquals("same-value", resolved.keycloakSub);
        assertEquals(1, AppUser.count("keycloakSub", "same-value"));
    }
}
