-- V13__create_orders_and_order_items_tables.sql
-- Create order aggregates for marketplace order management
-- Idempotent: uses WHERE NOT EXISTS checks and IF NOT EXISTS clauses

-- Create orders table (aggregate root)
-- Represents a purchase from one buyer to one seller
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PAYMENT' CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELLED')),
    -- Financial amounts (in cents to avoid float precision issues; or use DECIMAL for currency)
    gross_amount DECIMAL(19,2) NOT NULL CHECK (gross_amount > 0),
    commission_amount DECIMAL(19,2) NOT NULL DEFAULT 0 CHECK (commission_amount >= 0),
    seller_payout DECIMAL(19,2) NOT NULL DEFAULT 0 CHECK (seller_payout >= 0),
    -- Audit trail
    payment_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_buyer_id FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_seller_id FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_orders_buyer_id ON orders(buyer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_seller_id ON orders(seller_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_buyer_status ON orders(buyer_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_seller_status ON orders(seller_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);

-- Create order_items table (line items in an order)
CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(19,2) NOT NULL CHECK (unit_price > 0),
    subtotal DECIMAL(19,2) NOT NULL CHECK (subtotal > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_items_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product_id FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items(product_id);

-- Add table comments
COMMENT ON TABLE orders IS 'Represents a marketplace order from one buyer to one seller';
COMMENT ON COLUMN orders.buyer_id IS 'User ID of the buyer (foreign key)';
COMMENT ON COLUMN orders.seller_id IS 'User ID of the seller (foreign key)';
COMMENT ON COLUMN orders.status IS 'Order lifecycle state: PENDING_PAYMENT -> PAID -> SHIPPED -> DELIVERED -> COMPLETED';
COMMENT ON COLUMN orders.gross_amount IS 'Total purchase amount before commission';
COMMENT ON COLUMN orders.commission_amount IS 'Platform commission (5% of gross_amount)';
COMMENT ON COLUMN orders.seller_payout IS 'Amount seller receives after commission (95% of gross_amount)';

COMMENT ON TABLE order_items IS 'Individual line items within an order';
COMMENT ON COLUMN order_items.product_id IS 'Reference to the product being ordered';
COMMENT ON COLUMN order_items.quantity IS 'Number of units ordered';
COMMENT ON COLUMN order_items.unit_price IS 'Price per unit at time of order';
COMMENT ON COLUMN order_items.subtotal IS 'quantity * unit_price';

