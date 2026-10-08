/**
 * Identity and access management boundary.
 *
 * <p>Owns: users, credentials, roles, permissions, refresh tokens.
 * Provides JWT authentication with single-use refresh rotation, RBAC
 * enforcement and tenant-scoped sessions. No other module may own user
 * credentials.</p>
 */
package com.procureflow.identity;
