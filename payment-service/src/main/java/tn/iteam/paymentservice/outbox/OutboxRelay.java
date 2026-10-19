package tn.iteam.paymentservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.paymentservice.model.OutboxEvent;
import tn.iteam.paymentservice.repos.OutboxRepository;

import java.time.Instant;
import java.util.List;

/**
 * The transactional half of the outbox: claim rows, publish them, record the outcome.
 *
 * <p>Deliberately a separate bean from {@link OutboxPublisher}. Spring's
 * {@code @Transactional} is proxy-based, so a method calling another {@code
 * @Transactional} method <em>on itself</em> bypasses the proxy entirely — the
 * annotation would be ignored and {@code SELECT ... FOR UPDATE SKIP LOCKED} would run
 * in autocommit, taking and immediately releasing the row lock. That is precisely the
 * double-publish race this locking exists to prevent, so the calls have to cross a bean
 * boundary.
 */
@Service
@RequiredArgsConstructor
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaSender kafkaSender;

    /**
     * Claims up to {@code limit} pending events for this instance.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} lets several nodes drain the table
     * concurrently: a row another transaction already holds is skipped instead of
     * waited for, so no two instances process the same event.
     *
     * <p>{@code REQUIRES_NEW} commits the claim on its own, before the slow network
     * send, so a crash mid-publish does not roll the claim back and re-queue events
     * that may already have reached the broker.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claim(int limit) {
        return outboxRepository.claimPending(limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(OutboxEvent claimed) {
        try {
            kafkaSender.send(claimed);

            OutboxEvent locked = outboxRepository.lockByEventId(claimed.getEventId());
            locked.setStatus("SENT");
            locked.setSentAt(Instant.now());
            outboxRepository.save(locked);

            log.debug("Published outbox event {} ({})", claimed.getEventId(), claimed.getEventType());

        } catch (Exception ex) {
            log.warn("Failed to publish event {}: {}", claimed.getEventId(), ex.getMessage());
            recordFailure(claimed);
        }
    }

    private void recordFailure(OutboxEvent claimed) {
        OutboxEvent locked = outboxRepository.lockByEventId(claimed.getEventId());
        locked.setAttempts(locked.getAttempts() + 1);
        if (locked.getAttempts() >= MAX_ATTEMPTS) {
            locked.setStatus("FAILED");
            log.error("Event {} abandoned after {} attempts — manual reconciliation needed",
                    claimed.getEventId(), locked.getAttempts());
        }
        outboxRepository.save(locked);
    }
}