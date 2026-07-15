package com.sudothis.security;

import java.util.List;
import java.util.Map;

/**
 * Result of PermissionService.getFilterClauses(). Contains JPQL WHERE clauses and
 * their bound parameters to apply to a list query for tenant isolation.
 * A denied result means the actor has no access to the entity type at all.
 */
public record FilterResult(
    List<String> whereClauses,
    Map<String, Object> params,
    boolean denied
) {
    public static FilterResult allow() {
        return new FilterResult(List.of(), Map.of(), false);
    }

    public static FilterResult deny() {
        return new FilterResult(List.of(), Map.of(), true);
    }

    public static FilterResult withClauses(List<String> clauses, Map<String, Object> params) {
        return new FilterResult(clauses, params, false);
    }
}
