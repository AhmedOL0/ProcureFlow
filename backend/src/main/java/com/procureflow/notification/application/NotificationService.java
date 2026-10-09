package com.procureflow.notification.application;

import com.procureflow.notification.domain.AppNotification;
import com.procureflow.notification.infrastructure.NotificationRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.shared.web.ApiException;
import java.util.List;
import java.util.UUID;
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

    @Transactional(readOnly = true)
    public List<AppNotification> inbox(String tenantSlug, UUID userId) {
        return notifications.findAllByTenantIdAndUserIdOrderByCreatedAtDesc(
                tenants.requireTenantId(tenantSlug), userId);
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
