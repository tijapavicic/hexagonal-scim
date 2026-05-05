-- Runs once on the very first `docker compose up` when the data volume is empty.
-- Creates a dedicated database and owner for Keycloak so it stays isolated
-- from the application database (hexagonal_scim / scim).

CREATE USER keycloak WITH PASSWORD 'keycloak';
CREATE DATABASE keycloak OWNER keycloak;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;

