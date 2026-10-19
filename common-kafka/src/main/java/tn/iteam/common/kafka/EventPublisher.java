package tn.iteam.common.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Publishes domain events to Kafka without letting a broker outage break the
 * business operation.
 *
 * <p>{@code KafkaTemplate#send} is <em>not</em> fully asynchronous: the call
 * itself blocks the calling thread while it waits for topic metadata, up to
 * {@code max.block.ms} (60s by default). Calling it straight from a
 * {@code @Service} therefore turned every write into a 60s hang on the request
 * thread &mdash; the row was already committed to the database, but the client
 * was left staring at a spinner and, because the blocking call also starved the
 * servlet pool, the whole endpoint looked stuck. The catch block around
 * {@code send} does not help: the block happens <em>inside</em> it.
 *
 * <p>So the blocking {@code send} is handed to a small daemon executor and
 * {@link #publish} returns immediately. Failures are logged and swallowed,
 * because events are notifications, not part of the write path.
 *
 * <p>For events that must not be lost (anything driving money or inventory),
 * write the event to an outbox table inside the same transaction and publish it
 * from there, as payment-service does. Use this class for notifications.
 *
 * <p>Not annotated with {@code @Component}: each consuming service registers it
 * via {@code @Bean} so the dependency stays explicit.
 */
@Slf4j
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Dedicated daemon threads for the blocking {@code send}. Bounded so a broker
     * outage cannot turn into unbounded memory growth, and non-blocking on the
     * caller so an overloaded queue drops notifications rather than requests.
     */
    private static final ThreadPoolExecutor SENDER = new ThreadPoolExecutor(
            1, 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "kafka-event-sender");
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.DiscardPolicy()
    );

    /** Convenience constructor for string-only publishers. */
    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this(kafkaTemplate, new ObjectMapper());
    }

    public void publish(String topic, String message) {
        try {
            SENDER.execute(() -> doSend(topic, message));
        } catch (RejectedExecutionException e) {
            log.warn("Event queue full, dropping event for topic '{}': {}", topic, message);
        }
    }

    /** Runs on a sender thread; every failure mode here is non-fatal. */
    private void doSend(String topic, String message) {
        try {
            kafkaTemplate.send(topic, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Could not publish to topic '{}': {}", topic, ex.getMessage());
                        } else {
                            log.debug("Published to '{}': {}", topic, message);
                        }
                    });
        } catch (Exception e) {
            log.warn("Could not publish to topic '{}': {}", topic, e.getMessage());
        }
    }

    /**
     * Publishes a structured event as JSON. Consumers read it with a
     * JsonDeserializer, so event contracts stay self-describing.
     */
    public void publishJson(String topic, Object payload) {
        try {
            publish(topic, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise event for topic '{}': {}", topic, e.getMessage());
        }
    }
}
