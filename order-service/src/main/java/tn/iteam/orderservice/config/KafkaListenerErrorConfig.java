package tn.iteam.orderservice.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tn.iteam.common.kafka.Topics;

/**
 * Failure handling for the payment/inventory listeners.
 *
 * <p>Without an error handler, Spring's default retries a poison message forever and
 * the listener never advances: one malformed event would stall every order update
 * queued behind it. After a bounded number of attempts the message is republished to
 * {@code <topic>.DLT} and its offset is committed, so the backlog drains while the bad
 * message stays available for inspection.
 */
@Configuration
public class KafkaListenerErrorConfig {

    private static final long RETRIES = 3;
    private static final long RETRY_INTERVAL_MS = 500;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(template, (record, ex) ->
                        new TopicPartition(Topics.deadLetter(record.topic()), record.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, RETRIES));
        handler.setCommitRecovered(true);
        return handler;
    }
}