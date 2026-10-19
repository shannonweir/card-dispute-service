CREATE TABLE customers (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('INDIVIDUAL', 'BUSINESS')),
    display_name VARCHAR(160) NOT NULL,
    contact_email VARCHAR(255)
);
CREATE TABLE customer_identities (
    id UUID PRIMARY KEY,
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    customer_id UUID NOT NULL UNIQUE REFERENCES customers(id),
    CONSTRAINT uq_customer_identity UNIQUE (issuer, subject)
);
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    type VARCHAR(30) NOT NULL CHECK (type IN ('CURRENT', 'CREDIT_CARD', 'SAVINGS')),
    display_name VARCHAR(160) NOT NULL,
    masked_number VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL
);
CREATE INDEX idx_accounts_customer ON accounts(customer_id);
CREATE TABLE card_transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    merchant_name VARCHAR(160) NOT NULL,
    reference VARCHAR(40) NOT NULL UNIQUE,
    merchant_location VARCHAR(160),
    type VARCHAR(30) NOT NULL,
    merchant_category VARCHAR(100) NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    transaction_time TIMESTAMP WITH TIME ZONE NOT NULL,
    card_last_four VARCHAR(4) NOT NULL,
    status VARCHAR(30) NOT NULL
);
CREATE INDEX idx_transactions_account_time ON card_transactions(account_id, transaction_time DESC);
CREATE TABLE disputes (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES card_transactions(id),
    reference VARCHAR(50) NOT NULL UNIQUE,
    reason VARCHAR(50) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_note VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0
);
-- Preserve closed cases, but allow only one non-closed dispute per transaction.
CREATE UNIQUE INDEX uq_disputes_non_closed_transaction
    ON disputes (transaction_id) WHERE status <> 'CLOSED';
CREATE INDEX idx_disputes_created ON disputes(created_at DESC);
CREATE TABLE dispute_submissions (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    dispute_id UUID NOT NULL REFERENCES disputes(id),
    CONSTRAINT uq_submission_key UNIQUE(customer_id,idempotency_key)
);
CREATE TABLE dispute_events (
    id UUID PRIMARY KEY,
    dispute_id UUID NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    title VARCHAR(160) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    action_required BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_dispute_events_dispute_time ON dispute_events(dispute_id, occurred_at);
CREATE TABLE dispute_documents (
    id UUID PRIMARY KEY,
    dispute_id UUID NOT NULL REFERENCES disputes(id),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    content BYTEA NOT NULL
);
CREATE INDEX idx_documents_dispute ON dispute_documents(dispute_id, uploaded_at);
