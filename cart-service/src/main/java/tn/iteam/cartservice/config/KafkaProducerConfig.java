package tn.iteam.cartservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import tn.iteam.common.kafka.EventPublisher;

@Configuration
public class KafkaProducerConfig {

    /**
     * Cart events go straight to the broker. They used to be POSTed to
     * http://localhost:8085/api/kafka/publish, which no service exposes, so the
     * request 404'd and the event was silently dropped.
     */
    @Bean
    public EventPublisher eventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        return new EventPublisher(kafkaTemplate);
    }
}
