package com.procureflow.identity.infrastructure;

import com.procureflow.identity.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for users. Used only by the identity module. */
public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"tenant", "roles", "roles.permissions"})
    List<User> findAllByEmail(String email);

    @EntityGraph(attributePaths = {"tenant", "roles", "roles.permissions"})
    @Query("select u from User u where u.tenant.slug = :slug"
            + " and lower(u.email) like lower(concat('%', :query, '%'))")
    Page<User> findPageByTenantSlug(
            @Param("slug") String slug, @Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "roles", "roles.permissions"})
    @Query("select u from User u where u.tenant.slug = :slug and u.email = :email")
    Optional<User> findByTenantSlugAndEmail(@Param("slug") String slug, @Param("email") String email);

    @EntityGraph(attributePaths = {"tenant", "roles"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findDetailedById(@Param("id") UUID id);

    @Query("select count(u) > 0 from User u where u.id = :id and u.tenant.slug = :slug")
    boolean existsInTenant(@Param("id") UUID id, @Param("slug") String slug);
}
