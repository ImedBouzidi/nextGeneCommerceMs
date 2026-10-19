package tn.iteam.kafkaservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tn.iteam.common.kafka.Topics;

/**
 * Demo consumers for the two auth/order topics.
 *
 * <p>Topic names come from the shared {@link Topics} constants so they cannot
 * drift from the producers or from the NewTopic beans in KafkaConfig.
 */
@Service
public class MessageConsumer {

    @KafkaListener(topics = Topics.USER_REGISTERED, groupId = "kafka-service-group")
    public void consumeUserRegistered(String message) {
        System.out.println("Received from " + Topics.USER_REGISTERED + ": " + message);
    }

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "kafka-service-group")
    public void consumeOrderCreated(String message) {
        System.out.println("Received from " + Topics.ORDER_CREATED + ": " + message);
    }
}
