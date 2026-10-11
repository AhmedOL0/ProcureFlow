package com.procureflow.identity.infrastructure;

import com.procureflow.identity.domain.Invite;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for workspace invites. Used only by the identity module. */
public interface InviteRepository extends JpaRepository<Invite, UUID> {

    List<Invite> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("select i from Invite i where i.tokenHash = :hash and i.usedAt is null and i.expiresAt > :now")
    List<Invite> findLiveByTokenHash(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("update Invite i set i.usedAt = :now where i.id = :id and i.usedAt is null and i.expiresAt > :now")
    int consumeIfLive(@Param("id") UUID id, @Param("now") Instant now);
}
