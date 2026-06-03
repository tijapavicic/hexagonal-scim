-- Add role column to users table
-- Migration V20: Replace isSeller boolean with role enum (BUYER, SELLER, ADMIN)
--
-- This migration:
-- 1. Adds role column (defaults to BUYER)
-- 2. Migrates existing users based on isSeller flag (if it exists)
-- 3. Drops isSeller column (if it exists)
-- 4. Makes role non-null

-- Add role column (nullable initially for migration)
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20);

-- Migrate existing data (if isSeller column exists)
-- Convert isSeller = true -> SELLER
-- Convert isSeller = false -> BUYER
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'users' AND column_name = 'is_seller') THEN
        UPDATE users SET role = 'SELLER' WHERE is_seller = true AND role IS NULL;
        UPDATE users SET role = 'BUYER' WHERE is_seller = false AND role IS NULL;

        -- Drop old column
        ALTER TABLE users DROP COLUMN is_seller;
    END IF;
END$$;

-- Set default for any remaining null values
UPDATE users SET role = 'BUYER' WHERE role IS NULL;

-- Make role non-null and add default
ALTER TABLE users ALTER COLUMN role SET NOT NULL;
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'BUYER';

-- Add check constraint to ensure only valid roles
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'users_role_check') THEN
        ALTER TABLE users ADD CONSTRAINT users_role_check
            CHECK (role IN ('BUYER', 'SELLER', 'ADMIN'));
    END IF;
END$$;

