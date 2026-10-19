package tn.iteam.paymentservice.outbox;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.paymentservice.model.OutboxEvent;

import java.util.List;

/**
 * Drains the outbox table into Kafka.
 *
 * <p>Events are written by {@link OutboxWriter} inside the business transaction and
 * published from here afterwards, which is what makes them survive a broker outage:
 * nothing is lost when Kafka is down, the rows simply wait for the next poll.
 *
 * <p>This class only schedules and loops — it holds no transaction, because the old
 * version wrapped a whole 100-event batch in one and blocked on {@code send().get()},
 * pinning database connections for as long as the broker took to answer.
 */
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRelay relay;

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:1000}")
    public void pollAndPublish() {
        try {
            for (int round = 0; round < BATCH_SIZE; round++) {
                List<OutboxEvent> batch = relay.claim(BATCH_SIZE);
                if (batch.isEmpty()) {
                    return;
                }
                for (OutboxEvent event : batch) {
                    relay.publish(event);
                }
            }
        } catch (Exception e) {
            log.warn("Outbox publisher cycle failed: {}", e.getMessage(), e);
        }
    }
}