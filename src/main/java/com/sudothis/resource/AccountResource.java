package com.sudothis.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.sudothis.audit.AuditEvent;
import com.sudothis.model.AppUser;
import com.sudothis.security.KeycloakAdminService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.jboss.logging.Logger;

/**
 * Self-service account management. Every endpoint operates exclusively on the
 * authenticated caller's own identity (sub claim) — this resource never
 * manages other users; that is AdminDataResource/Keycloak territory.
 *
 * Identity fields (name, email, password) live in Keycloak and are updated
 * through {@link KeycloakAdminService}; the local AppUser row is kept in sync.
 * When Keycloak admin access is not configured (tests, %h2 dev), profile
 * edits apply locally only and password changes are unavailable.
 */
@Path("/account")
@Authenticated
public class AccountResource {

    private static final Logger LOG = Logger.getLogger(AccountResource.class);

    @Inject Template account;
    @Inject SecurityIdentity identity;
    @Inject KeycloakAdminService keycloak;
    @Inject Event<AuditEvent> auditEvents;

    // ------------------------------------------------------------------
    // Page
    // ------------------------------------------------------------------

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Transactional
    public TemplateInstance page(@QueryParam("msg") String msg, @QueryParam("err") String err) {
        AppUser user = currentUser();
        String firstName = "", lastName = "", email = user.email != null ? user.email : "";
        if (keycloak.isConfigured()) {
            try {
                JsonNode kc = keycloak.getUser(keycloak.resolveUserId(user.username));
                firstName = kc.path("firstName").asText("");
                lastName  = kc.path("lastName").asText("");
                email     = kc.path("email").asText(email);
            } catch (Exception e) {
                LOG.warnf("Could not load Keycloak profile for %s: %s", user.keycloakSub, e.getMessage());
            }
        }
        return account
            .data("username", user.username)
            .data("firstName", firstName)
            .data("lastName", lastName)
            .data("email", email)
            .data("isEmployee", user.isEmployee)
            .data("passwordAvailable", keycloak.isConfigured())
            .data("msg", msg)
            .data("err", err);
    }

    // ------------------------------------------------------------------
    // Profile (display name + email)
    // ------------------------------------------------------------------

    @POST
    @Path("/profile")
    @Transactional
    public Response updateProfile(@FormParam("firstName") String firstName,
                                  @FormParam("lastName") String lastName,
                                  @FormParam("email") String email) {
        AppUser user = currentUser();
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return redirect(null, "Please enter a valid email address.");
        }
        firstName = firstName == null ? "" : firstName.trim();
        lastName  = lastName == null ? "" : lastName.trim();
        if (firstName.isEmpty() || lastName.isEmpty()) {
            return redirect(null, "First and last name are required.");
        }
        try {
            if (keycloak.isConfigured()) {
                keycloak.updateProfile(keycloak.resolveUserId(user.username), firstName, lastName, email);
            }
            user.email = email;
            audit(user, "PROFILE_UPDATE",
                String.format("{\"firstName\":\"%s\",\"lastName\":\"%s\",\"email\":\"%s\"}",
                    firstName, lastName, email));
            return redirect("Profile updated.", null);
        } catch (Exception e) {
            LOG.errorf(e, "Profile update failed for %s", user.keycloakSub);
            return redirect(null, "Profile update failed — try again later.");
        }
    }

    // ------------------------------------------------------------------
    // Password
    // ------------------------------------------------------------------

    @POST
    @Path("/password")
    @Transactional
    public Response changePassword(@FormParam("currentPassword") String currentPassword,
                                   @FormParam("newPassword") String newPassword,
                                   @FormParam("confirmPassword") String confirmPassword) {
        AppUser user = currentUser();
        if (!keycloak.isConfigured()) {
            return redirect(null, "Password change is not available in this environment.");
        }
        if (newPassword == null || newPassword.length() < 12) {
            return redirect(null, "New password must be at least 12 characters.");
        }
        if (!newPassword.equals(confirmPassword)) {
            return redirect(null, "New passwords do not match.");
        }
        if (currentPassword == null || !keycloak.verifyPassword(user.username, currentPassword)) {
            audit(user, "PASSWORD_CHANGE_DENIED", "{\"reason\":\"current password verification failed\"}");
            return redirect(null, "Current password is incorrect.");
        }
        try {
            keycloak.resetPassword(keycloak.resolveUserId(user.username), newPassword);
            audit(user, "PASSWORD_CHANGE", "{}");
            return redirect("Password changed.", null);
        } catch (Exception e) {
            LOG.errorf(e, "Password change failed for %s", user.keycloakSub);
            return redirect(null, "Password change failed — try again later.");
        }
    }

    // ------------------------------------------------------------------
    // Account deletion (end-user accounts only)
    // ------------------------------------------------------------------

    @POST
    @Path("/delete")
    @Transactional
    public Response deleteAccount(@FormParam("confirm") String confirm) {
        AppUser user = currentUser();
        if (user.isEmployee) {
            audit(user, "ACCOUNT_DELETE_DENIED", "{\"reason\":\"company-managed account\"}");
            return Response.status(Response.Status.FORBIDDEN)
                .entity("Company-managed accounts can only be deleted by an administrator.")
                .build();
        }
        if (!"DELETE".equals(confirm)) {
            return redirect(null, "Type DELETE in the confirmation box to remove your account.");
        }
        try {
            if (keycloak.isConfigured()) {
                keycloak.deleteUser(keycloak.resolveUserId(user.username));
            }
            // Local row is deactivated and scrubbed of PII rather than removed:
            // AuditLog/AdminPermission/GroupMembership rows reference it and the
            // compliance trail must survive the account.
            user.active = false;
            user.email = null;
            audit(user, "ACCOUNT_DELETE", "{\"selfService\":true}");
            // OIDC session cookie still exists; /logout ends it and returns to /
            return Response.seeOther(URI.create("/logout")).build();
        } catch (Exception e) {
            LOG.errorf(e, "Account deletion failed for %s", user.keycloakSub);
            return redirect(null, "Account deletion failed — try again later.");
        }
    }

    // ------------------------------------------------------------------

    private AppUser currentUser() {
        String sub = identity.getAttribute("sub");
        String effectiveSub = sub != null ? sub : identity.getPrincipal().getName();
        return AppUser.ensureExists(effectiveSub, identity.getPrincipal().getName(),
            identity.getAttribute("email"));
    }

    private void audit(AppUser user, String action, String payload) {
        auditEvents.fire(new AuditEvent(user.keycloakSub, action, "ACCOUNT",
            String.valueOf(user.id), payload));
    }

    private Response redirect(String msg, String err) {
        String q = msg != null ? "?msg=" + msg.replace(" ", "+")
                 : err != null ? "?err=" + err.replace(" ", "+") : "";
        return Response.seeOther(URI.create("/account" + q)).build();
    }
}
