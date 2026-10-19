package tn.iteam.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import tn.iteam.common.kafka.EventPublisher;

/**
 * Order events are published straight to the broker rather than through a proxy
 * service: the broker already decouples producer from consumer, and routing
 * through HTTP would only add a hop and a shared point of failure.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public EventPublisher eventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        return new EventPublisher(kafkaTemplate);
    }
}
