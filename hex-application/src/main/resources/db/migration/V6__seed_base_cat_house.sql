INSERT INTO products (name, description, price, currency, stock_quantity)
SELECT
    'BaseCatHouse',
    'Starter cat house for one cat',
    79.99,
    'EUR',
    100
WHERE NOT EXISTS (
    SELECT 1 FROM products WHERE LOWER(name) = LOWER('BaseCatHouse')
);

