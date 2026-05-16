-- V11__add_seller_capabilities_to_users.sql
-- Extend users table with seller-specific capabilities for marketplace
-- Idempotent: uses WHERE NOT EXISTS checks and IF NOT EXISTS clauses

-- Add seller-specific columns to users table
ALTER TABLE users
ADD COLUMN IF NOT EXISTS is_seller BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS seller_verified_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS seller_display_name VARCHAR(255) UNIQUE,
ADD COLUMN IF NOT EXISTS seller_bio TEXT,
ADD COLUMN IF NOT EXISTS seller_rating DECIMAL(3,2) DEFAULT 0.0 CHECK (seller_rating >= 0 AND seller_rating <= 5),
ADD COLUMN IF NOT EXISTS seller_review_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS seller_joined_at TIMESTAMP;

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_users_is_seller ON users(is_seller);
CREATE INDEX IF NOT EXISTS idx_users_seller_rating ON users(seller_rating DESC) WHERE is_seller = true;
CREATE INDEX IF NOT EXISTS idx_users_seller_display_name ON users(seller_display_name) WHERE is_seller = true;

-- Optional: Add constraint to ensure seller_display_name is present if is_seller = true
-- This should be enforced at application level as well
COMMENT ON COLUMN users.is_seller IS 'Indicates if user is active seller';
COMMENT ON COLUMN users.seller_verified_at IS 'Timestamp when seller account was verified by platform admin';
COMMENT ON COLUMN users.seller_display_name IS 'Public-facing seller shop name (unique when is_seller=true)';
COMMENT ON COLUMN users.seller_bio IS 'Seller shop description (max 500 chars)';
COMMENT ON COLUMN users.seller_rating IS 'Average rating from buyer reviews (0-5 stars)';
COMMENT ON COLUMN users.seller_review_count IS 'Total number of reviews from buyers';
COMMENT ON COLUMN users.seller_joined_at IS 'Timestamp when seller account was created';

