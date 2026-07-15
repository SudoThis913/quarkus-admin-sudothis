package com.sudothis.meta;

/**
 * Implemented by entities that need a safe DTO projection for API responses.
 * AdminDataResource calls toDto() on each result before serialization, preventing
 * Hibernate-managed associations from leaking nested entity graphs to callers.
 */
public interface DtoProjector {
    Object toDto();
}
