-- Acquirer integration: everything needed to talk to a real payment provider.
--
-- Until now a "successful payment" was a row flipping to SUCCESS with no money
-- involved. These columns record what the acquirer actually did, so a charge can be
-- reconciled, captured, refunded or chased after a failure.

ALTER TABLE payments ADD COLUMN IF NOT EXISTS currency        VARCHAR(3);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS authorization_id VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS client_secret   VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS gateway         VARCHAR(40);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failure_code    VARCHAR(64);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS authorized_at   TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS captured_at     TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refunded_at     TIMESTAMP;

-- A charge must be uniquely identifiable at the provider, otherwise a retry could
-- produce two charges for one payment row.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_payments_authorization_id') THEN
        ALTER TABLE payments
            ADD CONSTRAINT uq_payments_authorization_id UNIQUE (authorization_id);
    END IF;
END $$;

-- Reconciliation and support queries: "what happened to this Stripe charge?",
-- "which payments are authorised but never captured?".
CREATE INDEX IF NOT EXISTS idx_payments_authorization ON payments (authorization_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments (payment_status, updated_at);