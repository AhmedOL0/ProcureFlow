package com.procureflow.identity.infrastructure;

import com.procureflow.identity.domain.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for mailbox-verification tokens. Used only by the identity module. */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update EmailVerificationToken t set t.usedAt = :now"
            + " where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now")
    int consumeIfLive(@Param("hash") String hash, @Param("now") Instant now);
}
