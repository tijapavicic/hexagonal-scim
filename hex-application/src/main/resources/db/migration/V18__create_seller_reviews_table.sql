-- V18__create_seller_reviews_table.sql
-- Track buyer reviews for sellers (ratings 1-5, comment)
-- Used to calculate seller_rating displayed in their profile
-- H2-compatible: no COMMENT ON statements (PostgreSQL-specific)

-- Create seller_reviews table
-- Buyers can leave one review per seller they transacted with
CREATE TABLE IF NOT EXISTS seller_reviews (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    order_id BIGINT,  -- Optional: reference to the order leading to this review
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(255),
    comment TEXT,
    helpful_count INTEGER DEFAULT 0 CHECK (helpful_count >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seller_reviews_seller_id FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_seller_reviews_buyer_id FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_seller_reviews_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL,
    CONSTRAINT uq_seller_reviews_buyer_seller UNIQUE (seller_id, buyer_id) -- One review per seller+buyer
);

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_seller_reviews_seller_id ON seller_reviews(seller_id);
CREATE INDEX IF NOT EXISTS idx_seller_reviews_buyer_id ON seller_reviews(buyer_id);
CREATE INDEX IF NOT EXISTS idx_seller_reviews_rating ON seller_reviews(seller_id, rating);
CREATE INDEX IF NOT EXISTS idx_seller_reviews_created_at ON seller_reviews(seller_id, created_at DESC);

