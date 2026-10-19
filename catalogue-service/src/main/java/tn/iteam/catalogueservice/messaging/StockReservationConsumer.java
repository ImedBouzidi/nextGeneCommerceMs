package tn.iteam.catalogueservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tn.iteam.catalogueservice.exceptions.InsufficientStockException;
import tn.iteam.catalogueservice.exceptions.ProductNotFoundException;
import tn.iteam.catalogueservice.service.ProductService;
import tn.iteam.common.kafka.EventPublisher;
import tn.iteam.common.kafka.Topics;
import tn.iteam.common.kafka.event.StockReservationFailedEvent;
import tn.iteam.common.kafka.event.StockReservedEvent;
import tn.iteam.common.kafka.event.StockReserveEvent;

/**
 * The only place in the platform where {@code product.quantity} changes.
 *
 * <p>Reacts to {@code stock.reserve} and always answers with exactly one of
 * {@code stock.reserved} or {@code stock.reservation-failed}, so the saga cannot
 * stall waiting for a decision nobody made.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockReservationConsumer {

    private final ProductService productService;
    private final EventPublisher eventPublisher;

    @KafkaListener(topics = Topics.STOCK_RESERVE, groupId = "catalogue-service-stock")
    public void onStockReserve(StockReserveEvent event) {
        try {
            Integer remaining = productService.reserveStock(
                    event.getProductId(), event.getQuantity(), event.getEventId());

            if (remaining == null) {
                // Duplicate delivery: it was already reserved, so the confirmation
                // was already published. Publishing again would just add noise.
                log.debug("Duplicate stock.reserve {} ignored", event.getEventId());
                return;
            }

            StockReservedEvent reserved = new StockReservedEvent();
            reserved.setOrderId(event.getOrderId());
            reserved.setProductId(event.getProductId());
            reserved.setQuantity(event.getQuantity());
            reserved.setRemainingQuantity(remaining);
            // Correlation back to the exact command, so payment-service can tie the
            // confirmation to the right order even if several are in flight.
            reserved.setEventId(event.getEventId());

            eventPublisher.publishJson(Topics.STOCK_RESERVED, reserved);
            log.info("stock.reserved published for order {} (event {})", event.getOrderId(), event.getEventId());

        } catch (InsufficientStockException e) {
            publishFailure(event, e.getMessage(), e.getRequested(), e.getAvailable());
        } catch (ProductNotFoundException e) {
            publishFailure(event, "Product no longer exists", event.getQuantity(), 0);
        } catch (Exception e) {
            // Unexpected: let it propagate. The listener's error handler retries and
            // then dead-letters the message rather than silently losing the order.
            log.error("Failed to reserve stock for order {}", event.getOrderId(), e);
            throw e;
        }
    }

    private void publishFailure(StockReserveEvent event, String reason, Integer requested, Integer available) {
        StockReservationFailedEvent failed = new StockReservationFailedEvent();
        failed.setOrderId(event.getOrderId());
        failed.setProductId(event.getProductId());
        failed.setRequestedQuantity(requested);
        failed.setAvailableQuantity(available);
        failed.setReason(reason);
        failed.setEventId(event.getEventId());

        eventPublisher.publishJson(Topics.STOCK_RESERVATION_FAILED, failed);
        log.warn("stock.reservation-failed for order {}: {}", event.getOrderId(), reason);
    }
}