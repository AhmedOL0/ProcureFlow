package com.procureflow.identity.application;

import com.procureflow.identity.infrastructure.UserRepository;
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
}
