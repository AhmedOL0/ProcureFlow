/**
 * Audit and compliance boundary.
 *
 * <p>Planned: append-only audit events for security-sensitive actions with
 * before/after payloads. Audit writes never fail the business transaction
 * they observe. Materializes with the Audit &amp; Compliance epic.</p>
 */
package com.procureflow.audit;
