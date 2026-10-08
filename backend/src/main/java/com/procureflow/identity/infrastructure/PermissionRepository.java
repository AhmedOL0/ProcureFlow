package com.procureflow.identity.infrastructure;

import com.procureflow.identity.domain.Permission;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence adapter for the global permission catalog. */
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findAllByCodeIn(Collection<String> codes);
}
