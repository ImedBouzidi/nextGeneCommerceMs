package tn.iteam.paymentservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.common.kafka.Topics;
import tn.iteam.common.kafka.event.OrderCreatedEvent;
import tn.iteam.paymentservice.model.OrderSnapshot;
import tn.iteam.paymentservice.repos.OrderSnapshotRepository;

import java.time.LocalDateTime;

/**
 * Keeps the local order read model in sync.
 *
 * <p>This is the only channel by which payment-service learns what an order is. It
 * used to fetch the order over HTTP on the request thread, which meant a slow or down
 * order-service either blocked or failed the customer's payment.
 *
 * <p>Upsert semantics: order events can arrive more than once (and can arrive after a
 * payment has already started), so the snapshot is overwritten rather than
 * insert-only.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSnapshotConsumer {

    private final OrderSnapshotRepository snapshotRepository;

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "payment-service-orders")
    @Transactional
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event.getOrderId() == null) {
            log.warn("order.created without an orderId — ignoring: {}", event.getEventId());
            return;
        }

        OrderSnapshot snapshot = snapshotRepository.findByOrderId(event.getOrderId())
                .orElseGet(() -> newOrderSnapshot(event.getOrderId()));

        snapshot.setUserId(event.getUserId());
        snapshot.setProductId(event.getProductId());
        snapshot.setQtyOrdered(event.getQtyOrdered());
        snapshot.setTotalAmount(event.getTotalAmount());
        snapshot.setOrderStatus(event.getOrderStatus());
        snapshot.setSourceEventId(event.getEventId());
        snapshot.setUpdatedAt(LocalDateTime.now());

        snapshotRepository.save(snapshot);
        log.debug("Order snapshot {} updated from event {}", event.getOrderId(), event.getEventId());
    }

    private OrderSnapshot newOrderSnapshot(Long orderId) {
        OrderSnapshot snapshot = new OrderSnapshot();
        snapshot.setOrderId(orderId);
        return snapshot;
    }
}