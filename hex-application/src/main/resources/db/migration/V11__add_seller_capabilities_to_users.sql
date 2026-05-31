-- V11__add_seller_capabilities_to_users.sql
-- Extend users table with seller-specific capabilities for marketplace
-- Idempotent: uses WHERE NOT EXISTS checks and IF NOT EXISTS clauses
-- H2-compatible: no partial indexes, no COMMENT ON COLUMN (PostgreSQL-specific)

-- Add seller-specific columns to users table (H2 and PostgreSQL compatible)
ALTER TABLE users ADD COLUMN is_seller BOOLEAN DEFAULT false;
ALTER TABLE users ADD COLUMN seller_verified_at TIMESTAMP;
ALTER TABLE users ADD COLUMN seller_display_name VARCHAR(255);
ALTER TABLE users ADD COLUMN seller_bio TEXT;
ALTER TABLE users ADD COLUMN seller_rating DECIMAL(3,2) DEFAULT 0.0;
ALTER TABLE users ADD COLUMN seller_review_count INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN seller_joined_at TIMESTAMP;

-- Add constraints separately
ALTER TABLE users ADD CONSTRAINT check_seller_rating CHECK (seller_rating >= 0 AND seller_rating <= 5);
ALTER TABLE users ADD CONSTRAINT unique_seller_display_name UNIQUE (seller_display_name);

-- Create indices for query performance (no partial/filtered indexes: H2 does not support WHERE clause on CREATE INDEX)
CREATE INDEX IF NOT EXISTS idx_users_is_seller ON users(is_seller);
CREATE INDEX IF NOT EXISTS idx_users_seller_rating ON users(seller_rating DESC);
CREATE INDEX IF NOT EXISTS idx_users_seller_display_name ON users(seller_display_name);
