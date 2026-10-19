package tn.iteam.orderservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.common.kafka.Topics;
import tn.iteam.common.kafka.event.PaymentFailedEvent;
import tn.iteam.common.kafka.event.PaymentPaidEvent;
import tn.iteam.common.kafka.event.StockReservationFailedEvent;
import tn.iteam.orderservice.enums.OrderStatus;
import tn.iteam.orderservice.enums.PaymentStatus;
import tn.iteam.orderservice.model.ProcessedEvent;
import tn.iteam.orderservice.model.Order;
import tn.iteam.orderservice.repositories.OrderRepository;
import tn.iteam.orderservice.repositories.ProcessedEventRepository;

/**
 * Reacts to payment outcomes instead of being called by payment-service over HTTP.
 *
 * <p>This is the end of order-service's participation in the charge: it no longer
 * receives {@code PUT /api/orders/{id}/payment-status} from the payment flow, and it
 * no longer calls catalogue-service to move stock. It simply finds out what happened.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private static final String CONSUMER = "order-service-payment";

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = Topics.PAYMENT_PAID, groupId = "order-service-payment")
    @Transactional
    public void onPaymentPaid(PaymentPaidEvent event) {
        if (alreadyProcessed(event.getEventId())) return;

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            // Unknown order: retrying will not help, so record it and move on rather
            // than blocking the topic forever.
            log.error("payment.paid for unknown order {}", event.getOrderId());
            markProcessed(event.getEventId());
            return;
        }

        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(order);
            log.info("Order {} marked PAID (payment {})", order.getId(), event.getPaymentId());
        } else {
            log.debug("Order {} already PAID — duplicate payment.paid ignored", order.getId());
        }

        markProcessed(event.getEventId());
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = "order-service-payment")
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (alreadyProcessed(event.getEventId())) return;

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.error("payment.failed for unknown order {}", event.getOrderId());
            markProcessed(event.getEventId());
            return;
        }

        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            orderRepository.save(order);
            log.warn("Order {} marked FAILED: {}", order.getId(), event.getReason());
        }

        markProcessed(event.getEventId());
    }

    /**
     * Compensating action: stock could not be reserved, so the order can never be
     * fulfilled. Cancelling it keeps the customer (and support) from seeing an order
     * that is accepted but impossible to deliver.
     */
    @KafkaListener(topics = Topics.STOCK_RESERVATION_FAILED, groupId = "order-service-stock")
    @Transactional
    public void onStockReservationFailed(StockReservationFailedEvent event) {
        if (alreadyProcessed(event.getEventId())) return;

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.error("stock.reservation-failed for unknown order {}", event.getOrderId());
            markProcessed(event.getEventId());
            return;
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.FAILED);
            orderRepository.save(order);
            log.warn("Order {} cancelled: stock reservation failed ({})", order.getId(), event.getReason());
        }

        markProcessed(event.getEventId());
    }

    private boolean alreadyProcessed(String eventId) {
        if (eventId == null) return false;
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Event {} already processed — skipping duplicate delivery", eventId);
            return true;
        }
        return false;
    }

    private void markProcessed(String eventId) {
        if (eventId != null) {
            processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER));
        }
    }
}