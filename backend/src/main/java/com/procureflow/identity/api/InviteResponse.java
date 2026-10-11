package com.procureflow.identity.api;

import com.procureflow.identity.domain.Invite;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Invite view: everything except the token, which travels only inside the
 * mailed link and is never returned by the API.
 */
public record InviteResponse(UUID id, String email, List<String> roles, Instant expiresAt, Instant createdAt) {

    public static InviteResponse from(Invite invite) {
        return new InviteResponse(
                invite.getId(),
                invite.getEmail(),
                Arrays.stream(invite.getRoles().split(",")).sorted().toList(),
                invite.getExpiresAt(),
                invite.getCreatedAt());
    }
}
