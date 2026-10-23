-- Add invoice + invoice items tables (models exist in code)

CREATE TABLE IF NOT EXISTS invoices (
    id         BIGSERIAL PRIMARY KEY,
    account_id UUID
);

CREATE TABLE IF NOT EXISTS invoice_item (
    id                BIGSERIAL PRIMARY KEY,
    plan_name         VARCHAR(255),
    description       VARCHAR(255),
    amount_in_cents   BIGINT,
    currency          VARCHAR(255),
    invoice_item_type VARCHAR(50),
    invoice_id        BIGINT,
    CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_invoice_item_invoice ON invoice_item (invoice_id);

CREATE SEQUENCE IF NOT EXISTS invoice_item_seq START WITH 1 INCREMENT BY 50;
