-- V10: Add comprehensive test data for manual testing
-- Includes a dedicated test user, sample products, accounts, and payments

-- ═══════════════════════════════════════════════════════════════════════════════
-- TEST USER
-- ═══════════════════════════════════════════════════════════════════════════════

-- Create primary test user (email: test@example.com)
-- Password in Keycloak: "Test123!" (see manual testing guide)
INSERT INTO users (email, display_name)
SELECT 'test@example.com', 'Test User'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE LOWER(email) = LOWER('test@example.com'));


-- ═══════════════════════════════════════════════════════════════════════════════
-- PRODUCTS
-- ═══════════════════════════════════════════════════════════════════════════════
-- Seed additional products for the art marketplace domain

INSERT INTO products (name, description, price, currency, stock_quantity)
SELECT
    'Premium CatHouse Deluxe',
    'Modern multi-level cat house with hammock and scratching posts',
    149.99,
    'EUR',
    50
WHERE NOT EXISTS (SELECT 1 FROM products WHERE LOWER(name) = LOWER('Premium CatHouse Deluxe'));

INSERT INTO products (name, description, price, currency, stock_quantity)
SELECT
    'Cat Playground Structure',
    'Interactive playground with climbing and hiding areas',
    199.99,
    'EUR',
    25
WHERE NOT EXISTS (SELECT 1 FROM products WHERE LOWER(name) = LOWER('Cat Playground Structure'));

INSERT INTO products (name, description, price, currency, stock_quantity)
SELECT
    'Digital Art License - Basic',
    'Single artwork license with limited distribution rights',
    29.99,
    'EUR',
    1000
WHERE NOT EXISTS (SELECT 1 FROM products WHERE LOWER(name) = LOWER('Digital Art License - Basic'));

INSERT INTO products (name, description, price, currency, stock_quantity)
SELECT
    'Digital Art License - Premium',
    'Premium artwork license with commercial usage rights',
    99.99,
    'EUR',
    500
WHERE NOT EXISTS (SELECT 1 FROM products WHERE LOWER(name) = LOWER('Digital Art License - Premium'));

INSERT INTO products (name, description, price, currency, stock_quantity)
SELECT
    'Art Gallery Collection Pass',
    'Annual pass to exclusive art gallery collections',
    249.00,
    'EUR',
    100
WHERE NOT EXISTS (SELECT 1 FROM products WHERE LOWER(name) = LOWER('Art Gallery Collection Pass'));


-- ═══════════════════════════════════════════════════════════════════════════════
-- ACCOUNTS
-- ═══════════════════════════════════════════════════════════════════════════════
-- Create accounts for test user (for account management feature testing)

INSERT INTO accounts (user_id, name)
SELECT u.id, 'Personal Checking'
FROM users u
WHERE LOWER(u.email) = LOWER('test@example.com')
AND NOT EXISTS (
    SELECT 1 FROM accounts a
    WHERE a.user_id = u.id AND a.name = 'Personal Checking'
);

INSERT INTO accounts (user_id, name)
SELECT u.id, 'Savings Account'
FROM users u
WHERE LOWER(u.email) = LOWER('test@example.com')
AND NOT EXISTS (
    SELECT 1 FROM accounts a
    WHERE a.user_id = u.id AND a.name = 'Savings Account'
);

INSERT INTO accounts (user_id, name)
SELECT u.id, 'Art Fund'
FROM users u
WHERE LOWER(u.email) = LOWER('test@example.com')
AND NOT EXISTS (
    SELECT 1 FROM accounts a
    WHERE a.user_id = u.id AND a.name = 'Art Fund'
);

-- Create accounts for some existing sample users for richer test scenarios
INSERT INTO accounts (user_id, name)
SELECT u.id, 'Main Account'
FROM users u
WHERE LOWER(u.email) = LOWER('alice@example.com')
AND NOT EXISTS (
    SELECT 1 FROM accounts a
    WHERE a.user_id = u.id AND a.name = 'Main Account'
);

INSERT INTO accounts (user_id, name)
SELECT u.id, 'Trading Account'
FROM users u
WHERE LOWER(u.email) = LOWER('bob@example.com')
AND NOT EXISTS (
    SELECT 1 FROM accounts a
    WHERE a.user_id = u.id AND a.name = 'Trading Account'
);


-- ═══════════════════════════════════════════════════════════════════════════════
-- PAYMENTS
-- ═══════════════════════════════════════════════════════════════════════════════
-- Create various payment records with different statuses for testing

-- PENDING payment (test user purchasing BaseCatHouse)
INSERT INTO payments (product_id, quantity, total_amount, currency, status, payment_method)
SELECT
    p.id,
    1,
    p.price,
    p.currency,
    'PENDING',
    'CREDIT_CARD'
FROM products p
WHERE LOWER(p.name) = LOWER('BaseCatHouse')
AND NOT EXISTS (
    SELECT 1 FROM payments WHERE product_id = p.id AND status = 'PENDING'
);

-- COMPLETED payment (test user purchased Premium CatHouse)
INSERT INTO payments (product_id, quantity, total_amount, currency, status, payment_method)
SELECT
    p.id,
    1,
    p.price,
    p.currency,
    'COMPLETED',
    'PAYPAL'
FROM products p
WHERE LOWER(p.name) = LOWER('Premium CatHouse Deluxe')
AND NOT EXISTS (
    SELECT 1 FROM payments WHERE product_id = p.id AND status = 'COMPLETED' LIMIT 1
);

-- FAILED payment attempt (art license)
INSERT INTO payments (product_id, quantity, total_amount, currency, status, payment_method)
SELECT
    p.id,
    1,
    p.price,
    p.currency,
    'FAILED',
    'IDEAL'
FROM products p
WHERE LOWER(p.name) = LOWER('Digital Art License - Basic')
AND NOT EXISTS (
    SELECT 1 FROM payments WHERE product_id = p.id AND status = 'FAILED' LIMIT 1
);

-- COMPLETED payment (refunded)
INSERT INTO payments (product_id, quantity, total_amount, currency, status, payment_method)
SELECT
    p.id,
    2,
    p.price * 2,
    p.currency,
    'COMPLETED',
    'PAYPAL'
FROM products p
WHERE LOWER(p.name) = LOWER('Cat Playground Structure')
AND NOT EXISTS (
    SELECT 1 FROM payments WHERE product_id = p.id AND status = 'COMPLETED' AND payment_method = 'PAYPAL' LIMIT 1
);

-- FAILED payment (cancelled)
INSERT INTO payments (product_id, quantity, total_amount, currency, status, payment_method)
SELECT
    p.id,
    1,
    p.price,
    p.currency,
    'FAILED',
    'BANK_ACCOUNT'
FROM products p
WHERE LOWER(p.name) = LOWER('Digital Art License - Premium')
AND NOT EXISTS (
    SELECT 1 FROM payments WHERE product_id = p.id AND status = 'FAILED' AND payment_method = 'BANK_ACCOUNT' LIMIT 1
);


-- ═══════════════════════════════════════════════════════════════════════════════
-- ACCOUNT BALANCES
-- ═══════════════════════════════════════════════════════════════════════════════
-- Add sample balances to accounts (if the balance column exists in the accounts table)
-- Note: This assumes there is a balance column added by V8__add_balance_to_accounts.sql

UPDATE accounts
SET balance = 5000.00
WHERE LOWER((SELECT email FROM users WHERE users.id = accounts.user_id)) = LOWER('test@example.com')
AND name = 'Personal Checking';

UPDATE accounts
SET balance = 15000.00
WHERE LOWER((SELECT email FROM users WHERE users.id = accounts.user_id)) = LOWER('test@example.com')
AND name = 'Savings Account';

UPDATE accounts
SET balance = 2500.00
WHERE LOWER((SELECT email FROM users WHERE users.id = accounts.user_id)) = LOWER('test@example.com')
AND name = 'Art Fund';

