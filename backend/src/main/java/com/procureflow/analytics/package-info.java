/**
 * Analytics boundary (read-only).
 *
 * <p>Planned: procurement KPIs, supplier KPIs, spending analytics, reporting.
 * This module reads from other modules but never writes to them; it owns no
 * transactional state of its own besides cached/precomputed views.
 * Materializes with the Analytics epic.</p>
 */
package com.procureflow.analytics;
