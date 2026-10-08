/**
 * Identity and access management boundary.
 *
 * <p>Owns: users, credentials, roles, permissions, refresh tokens.
 * Phase 2 (Identity &amp; Access epic): JWT authentication, RBAC enforcement,
 * tenant-scoped sessions. No other module may own user credentials.</p>
 */
package com.procureflow.identity;
