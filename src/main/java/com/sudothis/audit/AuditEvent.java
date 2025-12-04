package com.sudothis.audit;

public record AuditEvent(String actor, String action, String targetTable, String targetId, String payload) {}
