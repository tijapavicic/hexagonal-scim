CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(1000),
    price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    stock_quantity INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_products_name ON products (name);

