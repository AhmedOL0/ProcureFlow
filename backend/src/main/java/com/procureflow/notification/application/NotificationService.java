package com.procureflow.notification.application;

import com.procureflow.notification.domain.AppNotification;
import com.procureflow.notification.infrastructure.NotificationRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.shared.web.ApiException;
import com.procureflow.shared.web.Paged;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inbox reads. Members see their own rows; workspace admins may read the
 * tenant's rows for support. Cross-tenant access answers 404.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final TenantProvisioning tenants;

    public NotificationService(NotificationRepository notifications, TenantProvisioning tenants) {
        this.notifications = notifications;
        this.tenants = tenants;
    }

    /**
     * Paged inbox read, newest first. The inbox grows with every submission
     * and decision, so reads stay bounded; callers show {@code totalElements}
     * on their paginator controls.
     */
    @Transactional(readOnly = true)
    public Paged<AppNotification> inbox(String tenantSlug, UUID userId, int page, int size) {
        Page<AppNotification> found = notifications.findAllByTenantIdAndUserIdOrderByCreatedAtDesc(
                tenants.requireTenantId(tenantSlug), userId, PageRequest.of(page, size));
        return Paged.of(found.getContent(), page, size, found.getTotalElements());
    }

    @Transactional
    public AppNotification markRead(String tenantSlug, UUID id, UUID callerId, boolean admin) {
        AppNotification notification = notifications
                .findByIdAndTenantId(id, tenants.requireTenantId(tenantSlug))
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found"));
        if (!admin && !notification.getUserId().equals(callerId)) {
            throw ApiException.forbidden("NOT_OWNER", "Only the recipient or a workspace admin can do this");
        }
        notification.markRead();
        return notifications.save(notification);
    }
}
