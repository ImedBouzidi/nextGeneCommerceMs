-- Tunisian payment methods.
--
-- The previous CHECK constraint (V2) listed only the international set, so a
-- request carrying "EDINAR" or "D17" was rejected by the database before any code
-- ran — the same class of failure that broke COD payments earlier.

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_method_check;

ALTER TABLE payments ADD CONSTRAINT payments_payment_method_check
    CHECK (payment_method IS NULL OR payment_method IN
           ('CARD', 'COD', 'EDINAR', 'D17', 'CLICTOPAY', 'WALLET', 'PAYPAL', 'BANK_TRANSFER'));

-- Domestic instruments are always settled in dinars; recording the gateway makes
-- reconciliation per acquirer possible once more than one is configured.
CREATE INDEX IF NOT EXISTS idx_payments_gateway ON payments (gateway);