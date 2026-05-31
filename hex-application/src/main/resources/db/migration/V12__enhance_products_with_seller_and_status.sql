-- V12__enhance_products_with_seller_and_status.sql
-- Extend products table with seller ownership and marketplace status
-- H2-compatible: no IF NOT EXISTS on ALTER TABLE, no DO blocks, no partial indexes, no COMMENT ON

-- Add seller_id and marketplace status columns to products table
ALTER TABLE products ADD COLUMN seller_id BIGINT;
ALTER TABLE products ADD COLUMN is_active BOOLEAN DEFAULT true;
ALTER TABLE products ADD COLUMN published_at TIMESTAMP;

-- Add foreign key constraint to link products to sellers
ALTER TABLE products ADD CONSTRAINT fk_products_seller_id FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE;

-- Create indices for query performance (no partial indexes: H2 does not support WHERE clause)
CREATE INDEX IF NOT EXISTS idx_products_seller_id ON products(seller_id);
CREATE INDEX IF NOT EXISTS idx_products_is_active ON products(is_active);
CREATE INDEX IF NOT EXISTS idx_products_seller_active ON products(seller_id, is_active);
CREATE INDEX IF NOT EXISTS idx_products_published_at ON products(published_at DESC);
