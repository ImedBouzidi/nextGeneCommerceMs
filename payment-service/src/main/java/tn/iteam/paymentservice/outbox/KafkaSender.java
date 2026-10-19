package tn.iteam.paymentservice.outbox;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tn.iteam.common.kafka.Topics;
import tn.iteam.common.kafka.event.PaymentFailedEvent;
import tn.iteam.common.kafka.event.PaymentPaidEvent;
import tn.iteam.common.kafka.event.PaymentRequestedEvent;
import tn.iteam.common.kafka.event.StockReservationFailedEvent;
import tn.iteam.common.kafka.event.StockReserveEvent;
import tn.iteam.common.kafka.event.StockReservedEvent;
import tn.iteam.paymentservice.model.OutboxEvent;

/**
 * Knows which topic each event type belongs on.
 *
 * <p>Isolated behind its own bean so the mapping can be unit tested, and so
 * {@link OutboxRelay} does not depend on Kafka directly.
 */
@Component
@RequiredArgsConstructor
public class KafkaSender {

    private static final Logger log = LoggerFactory.getLogger(KafkaSender.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Sends one event and blocks until the broker acknowledges it.
     *
     * <p>The aggregate id is used as the message key, which pins every event about one
     * order to the same partition and therefore preserves their order.
     */
    public void send(OutboxEvent event) throws Exception {
        kafkaTemplate.send(mapEventToTopic(event.getEventType()),
                        event.getAggregateId(),
                        event.getPayload())
                .get();
    }

    /**
     * Event type to topic.
     *
     * <p>The previous switch matched "PAYMENT_PAID" while the service wrote
     * "payment.paid", so every real event fell through to "domain.events" — a topic
     * nothing consumed. Types now come from the shared event classes, so a topic name
     * cannot drift away from the event that produces it.
     */
    private String mapEventToTopic(String eventType) {
        if (eventType == null) return "domain.events";
        return switch (eventType) {
            case PaymentRequestedEvent.TYPE -> Topics.PAYMENT_REQUESTED;
            case PaymentPaidEvent.TYPE -> Topics.PAYMENT_PAID;
            case PaymentFailedEvent.TYPE -> Topics.PAYMENT_FAILED;
            case StockReserveEvent.TYPE -> Topics.STOCK_RESERVE;
            case StockReservedEvent.TYPE -> Topics.STOCK_RESERVED;
            case StockReservationFailedEvent.TYPE -> Topics.STOCK_RESERVATION_FAILED;
            default -> "domain.events";
        };
    }
}