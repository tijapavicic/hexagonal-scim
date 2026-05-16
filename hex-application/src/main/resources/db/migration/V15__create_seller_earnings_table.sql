-- V15__create_seller_earnings_table.sql
-- Track seller financial metrics: total_earned, total_paid_out, pending_payout
-- Idempotent: uses WHERE NOT EXISTS checks and IF NOT EXISTS clauses

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

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_seller_earnings_pending_payout ON seller_earnings(pending_payout DESC) WHERE pending_payout > 0;
CREATE INDEX IF NOT EXISTS idx_seller_earnings_updated_at ON seller_earnings(updated_at DESC);

-- Add table comments
COMMENT ON TABLE seller_earnings IS 'Aggregated financial summary for each seller: earned, paid out, and pending amounts';
COMMENT ON COLUMN seller_earnings.seller_id IS 'User ID of the seller (primary key, one row per seller)';
COMMENT ON COLUMN seller_earnings.total_earned IS 'Sum of all seller_payout amounts from transactions (95% of order gross)';
COMMENT ON COLUMN seller_earnings.total_paid_out IS 'Sum of all successful payout requests to seller bank account';
COMMENT ON COLUMN seller_earnings.pending_payout IS 'Available balance: total_earned - total_paid_out';
COMMENT ON COLUMN seller_earnings.last_payout_at IS 'Timestamp of the last successful payout request';

