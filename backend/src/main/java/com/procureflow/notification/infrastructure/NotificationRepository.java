package com.procureflow.notification.infrastructure;

import com.procureflow.notification.domain.AppNotification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for inbox rows. Used only by the notification module. */
public interface NotificationRepository extends JpaRepository<AppNotification, UUID> {

    Page<AppNotification> findAllByTenantIdAndUserIdOrderByCreatedAtDesc(
            UUID tenantId, UUID userId, Pageable pageable);

    Optional<AppNotification> findByIdAndTenantId(UUID id, UUID tenantId);
}
