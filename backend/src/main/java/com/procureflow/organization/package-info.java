/**
 * Organization and multi-tenancy boundary.
 *
 * <p>Owns: tenants, departments, organization memberships (companies arrive
 * in a later migration). Every tenant-scoped row in the system ultimately
 * belongs to a {@code Tenant} defined here. See
 * {@code docs/architecture/multi-tenancy.md} for the isolation strategy.</p>
 */
package com.procureflow.organization;
