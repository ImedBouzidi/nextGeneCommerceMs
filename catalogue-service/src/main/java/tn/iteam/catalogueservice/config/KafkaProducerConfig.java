package tn.iteam.catalogueservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tn.iteam.catalogueservice.service.ProductService;
import tn.iteam.common.kafka.EventPublisher;
import tn.iteam.common.kafka.Topics;

/**
 * Stock reservation consumer plumbing.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerConfig {

    /**
     * Catalogue events are notifications, so they are published with the shared
     * non-fatal publisher: a broker outage must not fail the category/product
     * write that has already been committed.
     */
    @Bean
    public EventPublisher eventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        return new EventPublisher(kafkaTemplate);
    }

    /**
     * A {@code stock.reserve} that cannot be applied after retries goes to
     * {@code stock.reserve.DLT} and the offset is committed, so one bad message
     * cannot block every later reservation behind it.
     */
    @Bean
    @ConditionalOnMissingBean(DefaultErrorHandler.class)
    public DefaultErrorHandler catalogueKafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(500L, 3L));
        handler.setCommitRecovered(true);
        return handler;
    }
}