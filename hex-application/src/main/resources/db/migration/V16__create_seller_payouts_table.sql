-- V16__create_seller_payouts_table.sql
-- Track payout requests from sellers (withdrawal requests to bank/payment processor)
-- Idempotent: uses WHERE NOT EXISTS checks and IF NOT EXISTS clauses

-- Create seller_payouts table
-- One record per payout request from seller
-- Tracks status: REQUESTED -> PROCESSING -> COMPLETED/FAILED
CREATE TABLE IF NOT EXISTS seller_payouts (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(50) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    payout_method VARCHAR(50),  -- 'bank_transfer', 'stripe', 'paypal', etc.
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    transaction_reference VARCHAR(255) UNIQUE,  -- Reference from payment processor
    failure_reason TEXT,  -- If status = FAILED, why did it fail?
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seller_payouts_seller_id FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT check_payout_dates CHECK (
        requested_at IS NOT NULL AND
        (processing_started_at IS NULL OR processing_started_at >= requested_at) AND
        (completed_at IS NULL OR completed_at >= COALESCE(processing_started_at, requested_at))
    )
);

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_seller_payouts_seller_id ON seller_payouts(seller_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_seller_payouts_status ON seller_payouts(status);
CREATE INDEX IF NOT EXISTS idx_seller_payouts_seller_status ON seller_payouts(seller_id, status);
CREATE INDEX IF NOT EXISTS idx_seller_payouts_requested_at ON seller_payouts(requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_seller_payouts_transaction_ref ON seller_payouts(transaction_reference);

-- Add table comments
COMMENT ON TABLE seller_payouts IS 'Payout request records: sellers request withdrawal of pending_payout balance';
COMMENT ON COLUMN seller_payouts.seller_id IS 'Seller user ID';
COMMENT ON COLUMN seller_payouts.amount IS 'Payout amount requested';
COMMENT ON COLUMN seller_payouts.status IS 'Payout lifecycle: REQUESTED -> PROCESSING -> COMPLETED (or FAILED/CANCELLED)';
COMMENT ON COLUMN seller_payouts.payout_method IS 'Destination: bank_transfer, stripe, paypal, etc.';
COMMENT ON COLUMN seller_payouts.transaction_reference IS 'Reference from payment processor for tracking and reconciliation';
COMMENT ON COLUMN seller_payouts.failure_reason IS 'Human-readable reason if payout failed';

