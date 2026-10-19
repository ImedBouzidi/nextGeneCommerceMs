package tn.iteam.paymentservice.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tn.iteam.common.kafka.Topics;

/**
 * Retry and dead-letter policy for the payment listeners.
 *
 * <p>Retries matter here beyond plain error handling: if a {@code payment.requested}
 * arrives before the matching {@code order.created} has been consumed, the consumer
 * throws and Kafka redelivers it once the read model has caught up. Without retries
 * that race would dead-letter a perfectly good payment on its first attempt.
 *
 * <p>The interval is short because the wait is milliseconds; a longer one only slows
 * genuinely broken messages on their way to the DLT.
 */
@Configuration
public class KafkaListenerErrorConfig {

    private static final long RETRIES = 5;
    private static final long RETRY_INTERVAL_MS = 500;

    @Bean
    public DefaultErrorHandler paymentKafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(template, (record, ex) ->
                        new TopicPartition(Topics.deadLetter(record.topic()), record.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, RETRIES));
        handler.setCommitRecovered(true);
        return handler;
    }
}