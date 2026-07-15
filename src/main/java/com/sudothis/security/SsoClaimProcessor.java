package com.sudothis.security;

import com.sudothis.model.AppUser;
import com.sudothis.model.SsoAttributeMapping;
import com.sudothis.model.SsoGroupMapping;
import com.sudothis.model.SsoProvider;
import com.sudothis.model.UserType;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class SsoClaimProcessor {

    private static final Logger LOG = Logger.getLogger(SsoClaimProcessor.class);

    @Inject
    PermissionService permissionService;

    @Transactional
    public void processLogin(AppUser user, SecurityIdentity identity) {
        SsoProvider provider = detectProvider(identity);
        if (provider == null) return;

        processGroupClaims(user, identity, provider);
        processAttributeClaims(user, identity, provider);
    }

    private SsoProvider detectProvider(SecurityIdentity identity) {
        String issuer = identity.getAttribute("iss");
        if (issuer == null) return SsoProvider.KEYCLOAK;

        if (issuer.contains("login.microsoftonline.com")) return SsoProvider.ENTRA;
        if (issuer.contains("accounts.google.com")) return SsoProvider.GOOGLE;
        return SsoProvider.KEYCLOAK;
    }

    private void processGroupClaims(AppUser user, SecurityIdentity identity, SsoProvider provider) {
        List<String> externalIds = extractGroupIds(identity, provider);
        if (externalIds.isEmpty()) return;

        List<SsoGroupMapping> mappings = SsoGroupMapping.list(
            "provider = ?1 and externalGroupId IN ?2 and active = true",
            provider, externalIds
        );

        for (SsoGroupMapping mapping : mappings) {
            if (!mapping.autoAssign || mapping.localGroup == null) continue;
            permissionService.ensureGroupMembership(user, mapping.localGroup, "sso-auto-assign");
        }
    }

    private void processAttributeClaims(AppUser user, SecurityIdentity identity, SsoProvider provider) {
        List<SsoAttributeMapping> attrMappings = SsoAttributeMapping.list(
            "provider = ?1 and active = true", provider
        );

        for (SsoAttributeMapping mapping : attrMappings) {
            Object claimValue = identity.getAttribute(mapping.attributeName);
            if (claimValue == null) continue;

            if (claimValue.toString().equals(mapping.attributeValue)) {
                applyAttributeMapping(user, mapping);
            }
        }
    }

    private void applyAttributeMapping(AppUser user, SsoAttributeMapping mapping) {
        switch (mapping.targetType) {
            case PERMISSION_GROUP -> {
                if (mapping.targetId != null) {
                    com.sudothis.model.PermissionGroup group =
                        com.sudothis.model.PermissionGroup.findById(mapping.targetId);
                    if (group != null && group.active) {
                        permissionService.ensureGroupMembership(user, group, "sso-attribute-mapping");
                    }
                }
            }
            case USER_TYPE -> {
                if (mapping.targetId != null && user.userType == null) {
                    UserType ut = UserType.findById(mapping.targetId);
                    if (ut != null && ut.active) {
                        user.userType = ut;
                        LOG.infof("Assigned UserType %s to user %s via SSO attribute mapping",
                            ut.name, user.keycloakSub);
                    }
                }
            }
            default -> LOG.debugf("Unsupported SSO attribute mapping targetType: %s", mapping.targetType);
        }
    }

    /**
     * Extracts external group identifiers from the JWT token per provider:
     * - ENTRA: "groups" claim → List of GUID strings
     * - GOOGLE: "hd" claim → hosted domain string (treated as single group identifier)
     * - KEYCLOAK: "groups" claim → List of group name strings (if group mapper configured)
     */
    private List<String> extractGroupIds(SecurityIdentity identity, SsoProvider provider) {
        List<String> ids = new ArrayList<>();

        switch (provider) {
            case ENTRA, KEYCLOAK -> {
                Object groups = identity.getAttribute("groups");
                if (groups instanceof Collection<?> col) {
                    for (Object g : col) { if (g != null) ids.add(g.toString()); }
                } else if (groups instanceof String s && !s.isBlank()) {
                    ids.add(s);
                }
            }
            case GOOGLE -> {
                // Google standard token does not include group membership.
                // "hd" is the hosted domain — treated as a group identifier for domain-level mapping.
                String hd = identity.getAttribute("hd");
                if (hd != null && !hd.isBlank()) ids.add(hd);
            }
            default -> { /* CUSTOM — no standard extraction */ }
        }

        return ids;
    }
}
