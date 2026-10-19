-- Local read model + consumer idempotency ledger for the event-driven payment flow.
--
-- order_snapshots replaces the synchronous GET /api/orders/{id} call that
-- payment-service used to make. It is populated from order.created events.
--
-- processed_events records which event ids have already been applied so Kafka's
-- at-least-once redelivery cannot charge or decrement twice.

CREATE TABLE IF NOT EXISTS order_snapshots (
    order_id         BIGINT PRIMARY KEY,
    user_id          BIGINT,
    product_id       BIGINT,
    qty_ordered      INTEGER,
    total_amount     NUMERIC(19, 2),
    order_status     VARCHAR(40),
    source_event_id  VARCHAR(36),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_order_snapshots_user ON order_snapshots (user_id);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id     VARCHAR(36) PRIMARY KEY,
    consumer     VARCHAR(100),
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);