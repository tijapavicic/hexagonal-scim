-- V15__create_seller_earnings_table.sql
-- Track seller financial metrics: total_earned, total_paid_out, pending_payout
-- H2-compatible: no partial indexes, no COMMENT ON statements (PostgreSQL-specific)

-- Create seller_earnings table
-- Maintained as sellers earn from transactions and request payouts
-- Formula: pending_payout = total_earned - total_paid_out
CREATE TABLE IF NOT EXISTS seller_earnings (
    seller_id BIGINT PRIMARY KEY,
    total_earned DECIMAL(19,2) NOT NULL DEFAULT 0.0 CHECK (total_earned >= 0),
    total_paid_out DECIMAL(19,2) NOT NULL DEFAULT 0.0 CHECK (total_paid_out >= 0),
    pending_payout DECIMAL(19,2) NOT NULL DEFAULT 0.0 CHECK (pending_payout >= 0),
    last_payout_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seller_earnings_seller_id FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indices for query performance (no partial indexes: H2 does not support WHERE clause)
CREATE INDEX IF NOT EXISTS idx_seller_earnings_pending_payout ON seller_earnings(pending_payout DESC);
CREATE INDEX IF NOT EXISTS idx_seller_earnings_updated_at ON seller_earnings(updated_at DESC);

