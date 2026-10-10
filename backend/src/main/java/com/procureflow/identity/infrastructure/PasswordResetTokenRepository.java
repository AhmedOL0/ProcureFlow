package com.procureflow.identity.infrastructure;

import com.procureflow.identity.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for password-reset tokens. Used only by the identity module. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Atomically consumes a live token. Returns 1 exactly when this call won
     * the race; concurrent redemptions observe 0 rows and must fail.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now "
            + "where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now")
    int consumeIfLive(@Param("hash") String tokenHash, @Param("now") Instant now);

    long countByUser_Id(UUID userId);
}
