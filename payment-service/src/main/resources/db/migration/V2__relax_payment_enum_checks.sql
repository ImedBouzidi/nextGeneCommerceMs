-- Hibernate's ddl-auto=update created enum CHECK constraints on the original
-- tables, listing only the values the enums had at the time:
--   payment_status: PENDING, SUCCESS, FAILED
--   payment_method: CARD, PAYPAL, WALLET
--
-- Those constraints make the two Phase 0 fixes impossible: PROCESSING/REFUNDED
-- were rejected, and so was COD — which is why every cash-on-delivery payment
-- failed. They are replaced here with the current value sets.
--
-- A fresh database built from V1 has no such constraints; the IF EXISTS guards
-- make this migration safe on both.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payments_payment_status_check') THEN
        ALTER TABLE payments DROP CONSTRAINT payments_payment_status_check;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'payments_payment_method_check') THEN
        ALTER TABLE payments DROP CONSTRAINT payments_payment_method_check;
    END IF;

    ALTER TABLE payments ADD CONSTRAINT payments_payment_status_check
        CHECK (payment_status IS NULL OR payment_status IN
               ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'REFUNDED'));

    ALTER TABLE payments ADD CONSTRAINT payments_payment_method_check
        CHECK (payment_method IS NULL OR payment_method IN
               ('CARD', 'COD', 'PAYPAL', 'WALLET', 'BANK_TRANSFER'));
END $$;