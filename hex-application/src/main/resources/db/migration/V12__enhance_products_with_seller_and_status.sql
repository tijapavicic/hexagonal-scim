-- V12__enhance_products_with_seller_and_status.sql
-- Extend products table with seller ownership and marketplace status
-- Idempotent: uses WHERE NOT EXISTS checks and IF NOT EXISTS clauses

-- Add seller_id and marketplace status columns to products table
ALTER TABLE products
ADD COLUMN IF NOT EXISTS seller_id BIGINT,
ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true,
ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

-- Add foreign key constraint to link products to sellers
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'products' AND constraint_name = 'fk_products_seller_id'
    ) THEN
        ALTER TABLE products
        ADD CONSTRAINT fk_products_seller_id
        FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END
$$;

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_products_seller_id ON products(seller_id);
CREATE INDEX IF NOT EXISTS idx_products_is_active ON products(is_active);
CREATE INDEX IF NOT EXISTS idx_products_seller_active ON products(seller_id, is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_products_published_at ON products(published_at DESC) WHERE is_active = true;

-- Add table comments
COMMENT ON COLUMN products.seller_id IS 'Foreign key to seller user account';
COMMENT ON COLUMN products.is_active IS 'Whether product is visible to buyers (soft delete equivalent)';
COMMENT ON COLUMN products.published_at IS 'Timestamp when product was first published to marketplace';

