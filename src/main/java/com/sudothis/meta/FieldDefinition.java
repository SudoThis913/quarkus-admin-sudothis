package com.sudothis.meta;

public record FieldDefinition(
    String name,        // Field name
    String type,        // Java type or specialized UI type
    boolean required,   // Is it nullable?
    boolean list,       // Is it a collection?
    String reference    // If type is RELATION, what entity does it point to?
) {}
