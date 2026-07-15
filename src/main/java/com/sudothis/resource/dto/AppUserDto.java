package com.sudothis.resource.dto;

import com.sudothis.model.AppUser;
import java.time.Instant;

/**
 * Projection of AppUser for API responses. Reference fields are collapsed to
 * their FK id (Long) using JPA attribute names so the admin UI field-name
 * mapping works without any special casing. No lazy association objects are
 * included, so no nested entity graphs can leak across tenant boundaries.
 */
public record AppUserDto(
    Long id,
    String username,
    String keycloakSub,
    String email,
    Instant createdAt,
    boolean active,
    boolean isEmployee,
    Long primaryCompany,   // FK id — attribute name matches AppUser.primaryCompany
    Long organization,     // FK id — attribute name matches AppUser.organization
    Long userType          // FK id — attribute name matches AppUser.userType
) {
    public static AppUserDto from(AppUser user) {
        return new AppUserDto(
            user.id,
            user.username,
            user.keycloakSub,
            user.email,
            user.createdAt,
            user.active,
            user.isEmployee,
            user.primaryCompany != null ? user.primaryCompany.id : null,
            user.organization   != null ? user.organization.id   : null,
            user.userType       != null ? user.userType.id       : null
        );
    }
}
