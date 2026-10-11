package com.procureflow.purchaseorder.infrastructure;

import com.procureflow.purchaseorder.domain.OrderItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence adapter for order lines. Used only by the purchaseorder module. */
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findAllByOrder_IdOrderByCreatedAt(UUID orderId);

    List<OrderItem> findAllByOrder_IdInOrderByOrder_IdAscCreatedAtAsc(Collection<UUID> orderIds);

    @Query("select i from OrderItem i where i.id = :id and i.order.id = :orderId")
    Optional<OrderItem> findByIdAndOrderId(@Param("id") UUID id, @Param("orderId") UUID orderId);
}
