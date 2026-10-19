package tn.iteam.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payment service.
 *
 * <p>No {@code @EnableFeignClients}: this service makes no outbound HTTP calls. It
 * learns about orders from {@code order.created} events, asks for stock through
 * {@code stock.reserve} and reports the outcome with {@code payment.paid} /
 * {@code payment.failed}. Every side effect is an event, so a broker or downstream
 * outage can no longer leave a payment committed while the order was never updated.
 *
 * <p>{@code @EnableScheduling} drives the outbox publisher, which is what makes
 * those events reliable.
 */
@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

}