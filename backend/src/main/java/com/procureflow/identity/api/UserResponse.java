package com.procureflow.identity.api;

import com.procureflow.identity.domain.Role;
import com.procureflow.identity.domain.User;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String status,
        boolean verified,
        String tenantSlug,
        List<String> roles) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                user.isVerified(),
                user.getTenant().getSlug(),
                user.getRoles().stream().map(Role::getName).sorted().toList());
    }
}
