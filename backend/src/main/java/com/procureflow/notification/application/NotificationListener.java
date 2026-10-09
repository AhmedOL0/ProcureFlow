package com.procureflow.notification.application;

import com.procureflow.approval.application.PurchaseRequestDecided;
import com.procureflow.notification.domain.AppNotification;
import com.procureflow.notification.infrastructure.NotificationRepository;
import com.procureflow.organization.application.TenantProvisioning;
import com.procureflow.procurement.application.PurchaseRequestSubmitted;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * First real consumer of domain events: request submitted / decided become
 * inbox rows for the requester plus a skeleton mail. Listeners run
 * {@code AFTER_COMMIT} in their own transaction, so a notification failure
 * can never roll back the business work it observes.
 */
@Service
public class NotificationListener {

    private final NotificationRepository notifications;
    private final NotificationTransport mail;
    private final TenantProvisioning tenants;

    public NotificationListener(
            NotificationRepository notifications, NotificationTransport mail, TenantProvisioning tenants) {
        this.notifications = notifications;
        this.mail = mail;
        this.tenants = tenants;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmitted(PurchaseRequestSubmitted event) {
        UUID tenantId = tenants.requireTenantId(event.tenantSlug());
        String title = "Request submitted: " + event.title();
        String body = "Your request is now awaiting approval.";
        notifications.save(new AppNotification(
                tenantId, event.requesterId(), "REQUEST_SUBMITTED", title, body));
        mail.send(tenantId, event.requesterId(), title, body);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDecided(PurchaseRequestDecided event) {
        UUID tenantId = tenants.requireTenantId(event.tenantSlug());
        String verdict = event.approved() ? "approved" : "rejected";
        String title = "Request " + verdict;
        String body = event.comment() == null || event.comment().isBlank()
                ? "Your request was " + verdict + "."
                : "Your request was " + verdict + ": " + event.comment();
        notifications.save(new AppNotification(
                tenantId, event.requesterId(), event.approved() ? "REQUEST_APPROVED" : "REQUEST_REJECTED",
                title, body));
        mail.send(tenantId, event.requesterId(), title, body);
    }
}
