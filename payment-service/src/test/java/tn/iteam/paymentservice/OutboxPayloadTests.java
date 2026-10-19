package tn.iteam.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tn.iteam.common.kafka.event.PaymentRequestedEvent;
import tn.iteam.paymentservice.outbox.OutboxWriter;
import tn.iteam.paymentservice.repos.OutboxRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox is the transport for every event in the platform. If the payload is not
 * the JSON the consumer will deserialise, the event is silently useless — the row
 * looks published, the consumer cannot read it.
 */
@SpringBootTest
class OutboxPayloadTests {

    @Autowired private OutboxWriter outboxWriter;
    @Autowired private OutboxRepository outboxRepository;

    @Test
    void stagedPayloadIsTheJsonTheConsumerWillDeserialise() throws Exception {
        PaymentRequestedEvent event = new PaymentRequestedEvent();
        event.setOrderId(4242L);
        event.setUserId("tester");
        event.setPaymentMethod("CARD");
        event.setSimulatedOutcome("DECLINED");

        outboxWriter.write(PaymentRequestedEvent.TYPE, "payment", "999999", event);

        var staged = outboxRepository.findAll().stream()
                .filter(e -> "999999".equals(e.getAggregateId()))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("nothing staged"));

        String storedInDatabase = readPayloadFromDatabase(staged.getEventId());

        outboxRepository.delete(staged);
        outboxRepository.flush();

        assertThat(storedInDatabase)
                .as("payload must be the event as JSON, not an internal id")
                .startsWith("{")
                .contains("\"orderId\":4242")
                .contains("\"simulatedOutcome\":\"DECLINED\"");
    }

    /**
     * Reads the row back over a fresh JDBC connection.
     *
     * <p>Deliberately not JPA: reading through the repository returns the in-memory
     * entity, which is exactly why a broken column mapping went unnoticed — the
     * object graph held the correct JSON while the table held an OID. This asserts on
     * what the consumer's {@code kafkaTemplate.send} would actually read.
     */
    @Autowired private DataSource dataSource;

    private String readPayloadFromDatabase(String eventId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select payload from outbox_events where event_id = ?")) {
            statement.setString(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).as("row must exist").isTrue();
                return rs.getString(1);
            }
        }
    }
}