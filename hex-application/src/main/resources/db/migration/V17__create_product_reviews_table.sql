-- V17__create_product_reviews_table.sql
-- Track buyer reviews for products (ratings 1-5, comment)
-- Idempotent: uses WHERE NOT EXISTS checks and IF NOT EXISTS clauses

-- Create product_reviews table
-- Buyers can leave one review per product they purchased
CREATE TABLE IF NOT EXISTS product_reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    order_id BIGINT,  -- Optional: reference to the order that this review is for
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(255),
    comment TEXT,
    helpful_count INTEGER DEFAULT 0 CHECK (helpful_count >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_reviews_product_id FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_reviews_buyer_id FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_reviews_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL,
    CONSTRAINT uq_product_reviews_buyer_product UNIQUE (product_id, buyer_id) -- One review per product+buyer
);

-- Create indices for query performance
CREATE INDEX IF NOT EXISTS idx_product_reviews_product_id ON product_reviews(product_id);
CREATE INDEX IF NOT EXISTS idx_product_reviews_buyer_id ON product_reviews(buyer_id);
CREATE INDEX IF NOT EXISTS idx_product_reviews_rating ON product_reviews(product_id, rating);
CREATE INDEX IF NOT EXISTS idx_product_reviews_created_at ON product_reviews(product_id, created_at DESC);

-- Add table comments
COMMENT ON TABLE product_reviews IS 'Buyer reviews for products (1-5 stars with optional comment)';
COMMENT ON COLUMN product_reviews.product_id IS 'The product being reviewed';
COMMENT ON COLUMN product_reviews.buyer_id IS 'Buyer who submitted the review';
COMMENT ON COLUMN product_reviews.rating IS 'Review rating: 1-5 stars';
COMMENT ON COLUMN product_reviews.title IS 'Short summary of review';
COMMENT ON COLUMN product_reviews.comment IS 'Detailed review comment';
COMMENT ON COLUMN product_reviews.helpful_count IS 'Number of users who found this review helpful';

