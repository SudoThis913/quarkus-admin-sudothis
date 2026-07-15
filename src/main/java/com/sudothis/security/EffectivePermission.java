package com.sudothis.security;

import com.sudothis.model.AdminPermission;

/**
 * A resolved, deduplicated permission entry for an actor.
 * Built by PermissionService.getEffectivePermissions() from direct assignments,
 * group memberships, and user type groups.
 */
public record EffectivePermission(
    AdminPermission.Role role,
    AdminPermission.ScopeType scopeType,
    Long targetId   // null for SITE scope
) {}
