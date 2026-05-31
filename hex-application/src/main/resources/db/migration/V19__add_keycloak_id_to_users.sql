-- V19__add_keycloak_id_to_users.sql
-- Add Keycloak user ID mapping to support OAuth2 identity lookups
-- H2-compatible: standard SQL only

-- Add keycloak_id column to users table
-- This maps internal user ID to external Keycloak user UUID
ALTER TABLE users ADD COLUMN keycloak_id VARCHAR(255);

-- Create unique index on keycloak_id for fast lookups
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_keycloak_id ON users(keycloak_id);

