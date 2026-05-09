import Keycloak from 'keycloak-js';

/**
 * Singleton Keycloak instance.
 * Reads configuration from VITE_ env vars with safe dev-mode defaults.
 */
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'hexagonal-scim',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'hexagonal-scim-public',
});

export default keycloak;

