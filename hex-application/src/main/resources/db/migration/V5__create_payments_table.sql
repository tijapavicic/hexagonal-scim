CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_method VARCHAR(32) NOT NULL
);

CREATE INDEX idx_payments_product_id ON payments (product_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_method ON payments (payment_method);

