package com.sudothis.model;

public interface TenantAware {
    Long getOrganizationId();
    Long getCompanyId();
    Long getTeamId();

    /**
     * JPQL path expression (relative to entity alias) used to filter this entity by organization.
     * Example: "organization.id" for Company, "id" for Organization itself.
     * Return null if this entity cannot be filtered by organization scope.
     */
    default String jpqlOrganizationIdPath() { return null; }

    /**
     * JPQL path expression for company-scope filtering.
     * Example: "id" for Company, "company.id" for Team.
     */
    default String jpqlCompanyIdPath() { return null; }

    /**
     * JPQL path expression for team-scope filtering.
     * Example: "id" for Team.
     */
    default String jpqlTeamIdPath() { return null; }
}
