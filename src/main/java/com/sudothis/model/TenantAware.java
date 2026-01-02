package com.sudothis.model;

public interface TenantAware {
    Long getOrganizationId();
    Long getCompanyId();
    Long getTeamId();
}
