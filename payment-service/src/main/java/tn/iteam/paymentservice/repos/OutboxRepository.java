package tn.iteam.paymentservice.repos;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.iteam.paymentservice.model.OutboxEvent;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    // Fetch all pending events ordered by creation time
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Fetches and locks the oldest pending events for processing.
     *
     * <p>Uses {@code SKIP LOCKED} so that several service instances can publish at the
     * same time without handing each other the same rows. The previous
     * {@code fetchAndLockOldest} blocked on locks instead, so every extra instance
     * just waited its turn — and the publisher actually used the unlocked
     * {@code findTop100ByStatus...}, which meant N instances published every event N
     * times.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'NEW'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimPending(@Param("limit") int limit);

    // Optional: fetch a specific event by ID and lock it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboxEvent o where o.eventId = :eventId")
    OutboxEvent lockByEventId(@Param("eventId") String eventId);

    // Optional: fetch top N events for batch processing
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);
}