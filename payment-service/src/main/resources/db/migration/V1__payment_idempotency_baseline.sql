-- Phase 0 baseline: make "one payment per order" a database guarantee.
--
-- This migration is written to be additive and re-runnable so it works both on a
-- fresh database (nothing exists yet) and on an existing dev database where the
-- tables were previously created by Hibernate's ddl-auto=update. It is applied at
-- baseline version 0 (see spring.flyway.baseline-version), so it always runs.

-- ---------------------------------------------------------------- payments
CREATE TABLE IF NOT EXISTS payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT                NOT NULL,
    amount          NUMERIC(19, 2),
    payment_status  VARCHAR(40),
    payment_method  VARCHAR(40),
    transaction_id  VARCHAR(255),
    idempotency_key VARCHAR(255),
    user_id         VARCHAR(255),
    failure_reason  TEXT,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP
);

-- Columns added after the original Hibernate-generated table.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS user_id         VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failure_reason  TEXT;

-- Money as double precision loses cents. Guarded because the conversion throws
-- if any legacy row cannot be cast (e.g. a non-numeric string).
DO $$
BEGIN
    EXECUTE 'ALTER TABLE payments
             ALTER COLUMN amount TYPE NUMERIC(19, 2)
             USING amount::NUMERIC(19, 2)';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'Skipping payments.amount conversion: %', SQLERRM;
END $$;

-- ------------------------------------------------------- dedupe legacy data
-- The pre-fix service had no unique constraint, so a double click left two rows
-- for the same order. Keep the earliest one and drop the rest, otherwise the
-- constraints below cannot be created.
DELETE FROM payments p
      USING payments keep
      WHERE p.order_id = keep.order_id
        AND p.id > keep.id;

DELETE FROM payments p
      USING payments keep
      WHERE p.idempotency_key IS NOT NULL
        AND p.idempotency_key = keep.idempotency_key
        AND p.id > keep.id;

-- The old service generated transaction ids from System.currentTimeMillis(), so
-- concurrent clicks could collide. Blank the duplicates rather than deleting rows:
-- NULLs are always distinct in Postgres, so this is safe.
UPDATE payments p
   SET transaction_id = NULL
  FROM payments keep
 WHERE p.transaction_id IS NOT NULL
   AND p.transaction_id = keep.transaction_id
   AND p.id > keep.id;

-- ---------------------------------------------------------------- constraints
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_payments_order_id') THEN
        ALTER TABLE payments ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_payments_idempotency_key') THEN
        -- Partial: the fallback key is NULL for legacy rows, and NULLs are always
        -- distinct in Postgres so they never collide.
        ALTER TABLE payments
            ADD CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_payments_transaction_id') THEN
        ALTER TABLE payments
            ADD CONSTRAINT uq_payments_transaction_id UNIQUE (transaction_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments (user_id);
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments (created_at DESC);

-- -------------------------------------------------------------- outbox_events
CREATE TABLE IF NOT EXISTS outbox_events (
    event_id     VARCHAR(36) PRIMARY KEY,
    aggregate    VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type   VARCHAR(255) NOT NULL,
    payload      TEXT,
    status       VARCHAR(40)   NOT NULL DEFAULT 'NEW',
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    sent_at      TIMESTAMP,
    attempts     INTEGER       NOT NULL DEFAULT 0
);

-- The publisher claims work with SELECT ... FOR UPDATE SKIP LOCKED; without this
-- index every poll scans the whole table.
CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON outbox_events (status, created_at);