import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'https://localhost:8443',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'hexagonal-scim',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'hexagonal-scim-public',
});

let initPromise: Promise<boolean> | null = null;
let refreshTimer: number | null = null;

/**
 * Initializes redirect-based login exactly once for the browser session.
 */
export function initAuth(): Promise<boolean> {
  if (!initPromise) {
    initPromise = keycloak.init({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    }).then((authenticated) => {
      if (authenticated && refreshTimer === null) {
        // Keep the token fresh so next API work can reuse the same session.
        refreshTimer = window.setInterval(() => {
          keycloak.updateToken(30).catch(() => {
            keycloak.logout({ redirectUri: window.location.origin });
          });
        }, 60_000);
      }
      return authenticated;
    });
  }

  return initPromise;
}

export function disposeAuthRefresh(): void {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

export function getKeycloak(): Keycloak {
  return keycloak;
}

