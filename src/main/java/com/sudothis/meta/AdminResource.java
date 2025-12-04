package com.sudothis.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity as managed by the Admin Framework.
 * <p>
 * Entities with this annotation will be automatically discovered by the ModelRegistry
 * and exposed via the generic Admin API.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AdminResource {
    /**
     * Friendly display name for the resource (e.g., "Users").
     * Defaults to the simple class name if empty.
     */
    String displayName() default "";

    /**
     * Whether this resource is read-only in the admin UI.
     */
    boolean readOnly() default false;
}
