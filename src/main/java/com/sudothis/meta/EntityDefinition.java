package com.sudothis.meta;

import java.util.List;
import java.util.Set;

/**
 * Defines the schema for a managed entity.
 */
public record EntityDefinition(
    String name,           // Simple class name (e.g., "AppUser")
    String displayName,    // Friendly name (e.g., "Users")
    String apiPath,        // API resource path (e.g., "/api/admin/AppUser")
    boolean readOnly,
    List<FieldDefinition> fields,
    Set<String> sortableFields  // Field names permitted as sort parameter values
) {}
