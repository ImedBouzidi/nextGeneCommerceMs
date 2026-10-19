package tn.iteam.common.kafka;

import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A broker outage must not break writes: the database transaction has already
 * committed by the time the event is published.
 */
class EventPublisherTest {

    @SuppressWarnings("unchecked")
    @Test
    void swallowsSynchronousSendFailure() throws InterruptedException {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), anyString())).thenThrow(new KafkaException("Send failed"));

        EventPublisher publisher = new EventPublisher(template);

        assertDoesNotThrow(() -> publisher.publish(Topics.CATEGORY_EVENTS, "New category created: laptops"));
        // Give the sender thread a moment so the swallowed failure is actually hit.
        Thread.sleep(200);
    }

    @SuppressWarnings("unchecked")
    @Test
    void swallowsAsynchronousSendFailure() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(
                new TimeoutException("Topic category-events not present in metadata after 60000 ms."));
        when(template.send(anyString(), anyString())).thenReturn(failed);

        EventPublisher publisher = new EventPublisher(template);

        assertDoesNotThrow(() -> publisher.publish(Topics.CATEGORY_EVENTS, "New category created: laptops"));
    }

    /**
     * Regression test: {@code KafkaTemplate#send} blocks the calling thread for
     * up to {@code max.block.ms} (60s by default) while it waits for topic
     * metadata. A {@code @Service} calling it inline turned every category
     * create into a 60s hang on the request thread even though the row was
     * already committed, leaving the UI spinner running forever.
     */
    @SuppressWarnings("unchecked")
    @Test
    void publishReturnsWithoutWaitingForBlockedSend() throws InterruptedException {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        CountDownLatch sendEntered = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        when(template.send(anyString(), anyString())).thenAnswer(invocation -> {
            sendEntered.countDown();
            try {
                releaseSend.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return CompletableFuture.completedFuture(null);
        });

        EventPublisher publisher = new EventPublisher(template);

        long start = System.nanoTime();
        publisher.publish(Topics.CATEGORY_EVENTS, "New category created: laptops");
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(sendEntered.await(5, TimeUnit.SECONDS), "send was never attempted");
        assertTrue(elapsedMillis < 1_000,
                "publish() must not block on send(); took " + elapsedMillis + "ms");
        releaseSend.countDown();
    }
}
