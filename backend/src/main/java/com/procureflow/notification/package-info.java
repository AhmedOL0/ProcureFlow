/**
 * Notification boundary.
 *
 * <p>Planned: email and in-app notifications, processed asynchronously from
 * domain events (see {@code shared.kernel}). No synchronous calls from other
 * modules; notification subscribes to events. Materializes with the
 * Notifications epic (Phase 4).</p>
 */
package com.procureflow.notification;
