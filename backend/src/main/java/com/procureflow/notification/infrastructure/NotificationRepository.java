package com.procureflow.notification.infrastructure;

import com.procureflow.notification.domain.AppNotification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for inbox rows. Used only by the notification module. */
public interface NotificationRepository extends JpaRepository<AppNotification, UUID> {

    List<AppNotification> findAllByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);

    Optional<AppNotification> findByIdAndTenantId(UUID id, UUID tenantId);
}
