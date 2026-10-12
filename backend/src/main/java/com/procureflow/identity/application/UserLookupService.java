package com.procureflow.identity.application;

import com.procureflow.identity.infrastructure.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link UserLookup}: tenant-scoped existence over identity's own repository. */
@Service
public class UserLookupService implements UserLookup {

    private final UserRepository users;

    public UserLookupService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsInTenant(UUID userId, String tenantSlug) {
        return users.existsInTenant(userId, tenantSlug);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserLookup.Card> cardInTenant(UUID userId, String tenantSlug) {
        return users
                .findByIdAndTenantSlug(userId, tenantSlug)
                .map(user -> new UserLookup.Card(
                        user.getId(), user.getEmail(), user.getFirstName(), user.getLastName()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserLookup.Card> cardsInTenant(Set<UUID> userIds, String tenantSlug) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UserLookup.Card> cards = new HashMap<>();
        for (var user : users.findByIdInAndTenantSlug(userIds, tenantSlug)) {
            cards.put(
                    user.getId(),
                    new UserLookup.Card(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName()));
        }
        return cards;
    }
}
