-- V14__create_transactions_table.sql
-- Create financial transaction records with commission split tracking
-- H2-compatible: no COMMENT ON statements (PostgreSQL-specific)

-- Create transactions table
-- Each successful order payment creates one transaction record
-- Transaction records the commission split (5% platform, 95% seller)
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE, -- One transaction per order
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    -- Financial amounts
    gross_amount DECIMAL(19,2) NOT NULL CHECK (gross_amount > 0),
    commission_amount DECIMAL(19,2) NOT NULL DEFAULT 0 CHECK (commission_amount >= 0),
    seller_payout DECIMAL(19,2) NOT NULL CHECK (seller_payout >= 0),
    -- Payment tracking
    payment_id BIGINT,
    payment_status VARCHAR(50),
    transaction_reference VARCHAR(255) UNIQUE, -- For idempotency and reconciliation
    -- Audit trail
    status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_buyer_id FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_seller_id FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_product_id FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_transactions_order_id ON transactions(order_id);
CREATE INDEX IF NOT EXISTS idx_transactions_buyer_id ON transactions(buyer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_seller_id ON transactions(seller_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_payment_id ON transactions(payment_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_payment_reference ON transactions(transaction_reference);

