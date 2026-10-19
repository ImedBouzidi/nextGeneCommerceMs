package tn.iteam.kafkaservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import tn.iteam.common.kafka.Topics;

/**
 * Single registry of every topic in the platform.
 *
 * <p>Declaring all topics here (rather than relying on the broker's
 * auto-creation) keeps partitions and replication factors deterministic and
 * prevents services that start first from implicitly shaping a topic's config.
 * kafka-service is the owner because topic creation is an infrastructure concern
 * that does not belong to any single bounded context.
 *
 * <p>Producer-side services publish to these topics directly; they only depend
 * on the names, not on this service being online.
 */
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.partitions:1}")
    private int partitions;

    @Value("${app.kafka.replicas:1}")
    private short replicas;

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    // ---------- auth ----------
    @Bean
    public NewTopic userRegisteredTopic() {
        return topic(Topics.USER_REGISTERED);
    }

    // ---------- catalogue ----------
    @Bean
    public NewTopic categoryEventsTopic() {
        return topic(Topics.CATEGORY_EVENTS);
    }

    @Bean
    public NewTopic productEventsTopic() {
        return topic(Topics.PRODUCT_EVENTS);
    }

    // ---------- order ----------
    @Bean
    public NewTopic orderCreatedTopic() {
        return topic(Topics.ORDER_CREATED);
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return topic(Topics.ORDER_CANCELLED);
    }

    @Bean
    public NewTopic orderApprovedTopic() {
        return topic(Topics.ORDER_APPROVED);
    }

    // ---------- payment ----------
    @Bean
    public NewTopic paymentStatusUpdatedTopic() {
        return topic(Topics.PAYMENT_STATUS_UPDATED);
    }

    @Bean
    public NewTopic paymentRequestedTopic() {
        return topic(Topics.PAYMENT_REQUESTED);
    }

    @Bean
    public NewTopic paymentPaidTopic() {
        return topic(Topics.PAYMENT_PAID);
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return topic(Topics.PAYMENT_FAILED);
    }

    // ---------- inventory ----------
    @Bean
    public NewTopic stockReserveTopic() {
        return topic(Topics.STOCK_RESERVE);
    }

    @Bean
    public NewTopic stockReservedTopic() {
        return topic(Topics.STOCK_RESERVED);
    }

    @Bean
    public NewTopic stockReservationFailedTopic() {
        return topic(Topics.STOCK_RESERVATION_FAILED);
    }

    // ---------- dead letter ----------
    // Declared explicitly: the recoverer publishes to <topic>.DLT, and a broker with
    // auto-creation disabled (or a different ACL) would otherwise drop exactly the
    // events that need investigating.
    @Bean
    public NewTopic paymentRequestedDlt() {
        return topic(Topics.deadLetter(Topics.PAYMENT_REQUESTED));
    }

    @Bean
    public NewTopic paymentPaidDlt() {
        return topic(Topics.deadLetter(Topics.PAYMENT_PAID));
    }

    @Bean
    public NewTopic paymentFailedDlt() {
        return topic(Topics.deadLetter(Topics.PAYMENT_FAILED));
    }

    @Bean
    public NewTopic stockReserveDlt() {
        return topic(Topics.deadLetter(Topics.STOCK_RESERVE));
    }

    @Bean
    public NewTopic stockReservedDlt() {
        return topic(Topics.deadLetter(Topics.STOCK_RESERVED));
    }

    @Bean
    public NewTopic stockReservationFailedDlt() {
        return topic(Topics.deadLetter(Topics.STOCK_RESERVATION_FAILED));
    }

    @Bean
    public NewTopic orderCreatedDlt() {
        return topic(Topics.deadLetter(Topics.ORDER_CREATED));
    }
}
