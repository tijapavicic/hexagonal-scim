import Keycloak from 'keycloak-js';

export type AuthErrorReason =
  | 'keycloak_unreachable'
  | 'invalid_client_or_redirect'
  | 'session_timeout'
  | 'unknown';

export interface AuthErrorDetails {
  reason: AuthErrorReason;
  message: string;
  cause?: unknown;
}

export interface AuthProfile {
  preferredUsername: string;
  realmRoles: string[];
  tokenExpiresAt: string | null;
}

export type AuthLifecycleEvent = 'onAuthSuccess' | 'onTokenExpired' | 'onAuthLogout' | 'onAuthError';

type AuthListener = (payload?: AuthErrorDetails) => void;

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'https://localhost:8443',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'hexagonal-scim',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'hexagonal-scim-public',
});

const listeners: Record<AuthLifecycleEvent, Set<AuthListener>> = {
  onAuthSuccess: new Set<AuthListener>(),
  onTokenExpired: new Set<AuthListener>(),
  onAuthLogout: new Set<AuthListener>(),
  onAuthError: new Set<AuthListener>(),
};

let initPromise: Promise<boolean> | null = null;
let refreshTimer: number | null = null;
let lifecycleBound = false;
let beforeUnloadBound = false;

function emit(event: AuthLifecycleEvent, payload?: AuthErrorDetails): void {
  listeners[event].forEach((listener) => listener(payload));
}

function classifyAuthError(error: unknown): AuthErrorDetails {
  const raw = error instanceof Error ? error.message : String(error ?? '');
  const lower = raw.toLowerCase();

  if (
    lower.includes('failed to fetch') ||
    lower.includes('network') ||
    lower.includes('econnrefused') ||
    lower.includes('enotfound')
  ) {
    return {
      reason: 'keycloak_unreachable',
      message: 'Keycloak is unreachable. Check container status, TLS certs, and local network.',
      cause: error,
    };
  }

  if (
    lower.includes('invalid_client') ||
    lower.includes('client not found') ||
    lower.includes('invalid redirect') ||
    lower.includes('invalid_redirect_uri') ||
    lower.includes('redirect_uri')
  ) {
    return {
      reason: 'invalid_client_or_redirect',
      message: 'Invalid Keycloak client or redirect URI configuration.',
      cause: error,
    };
  }

  if (
    lower.includes('login_required') ||
    lower.includes('session') ||
    lower.includes('token is not active') ||
    lower.includes('refresh token') ||
    lower.includes('invalid refresh token') ||
    lower.includes('invalid_token')
  ) {
    return {
      reason: 'session_timeout',
      message: 'Session timed out or refresh token is invalid. Please sign in again.',
      cause: error,
    };
  }

  return {
    reason: 'unknown',
    message: 'Authentication failed unexpectedly.',
    cause: error,
  };
}

function bindLifecycleEvents(): void {
  if (lifecycleBound) {
    return;
  }

  lifecycleBound = true;

  keycloak.onAuthSuccess = () => {
    emit('onAuthSuccess');
  };

  keycloak.onTokenExpired = () => {
    emit('onTokenExpired');
  };

  keycloak.onAuthLogout = () => {
    emit('onAuthLogout');
  };

  keycloak.onAuthError = (errorData) => {
    emit('onAuthError', classifyAuthError(errorData));
  };
}

function bindBeforeUnloadEvent(): void {
  if (beforeUnloadBound) {
    return;
  }

  beforeUnloadBound = true;

  window.addEventListener('beforeunload', () => {
    // Logout when user closes the browser/tab
    void logout();
  });
}

/**
 * Initializes redirect-based login exactly once for the browser session.
 */
export function initAuth(): Promise<boolean> {
  bindLifecycleEvents();

  if (!initPromise) {
    initPromise = keycloak
      .init({
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .then((authenticated) => {
        if (authenticated && refreshTimer === null) {
          // Keep the token fresh so next API work can reuse the same session.
          refreshTimer = window.setInterval(() => {
            keycloak
              .updateToken(30)
              .catch((error) => {
                // Log token refresh errors but don't fatally stop the app
                const details = classifyAuthError(error);
                console.warn('Token refresh failed:', details);
                emit('onAuthError', details);

                // If token is invalid/expired, force re-authentication
                if (details.reason === 'session_timeout' ||
                    (error instanceof Error && error.message?.includes('Invalid refresh token'))) {
                  console.info('Refresh token invalid, redirecting to login');
                  void keycloak.logout({ redirectUri: window.location.origin });
                }
              });
          }, 60_000);
          // Bind beforeunload event to logout when browser is closed
          bindBeforeUnloadEvent();
        }

        return authenticated;
      })
      .catch((error) => {
        const details = classifyAuthError(error);
        emit('onAuthError', details);
        throw details;
      });
  }

  return initPromise;
}

export function onAuthEvent(event: AuthLifecycleEvent, listener: AuthListener): () => void {
  listeners[event].add(listener);

  return () => {
    listeners[event].delete(listener);
  };
}

export function login(): Promise<void> {
  return keycloak.login();
}

export function logout(): Promise<void> {
  return keycloak.logout({ redirectUri: window.location.origin });
}

export function disposeAuthRefresh(): void {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

export function getAuthProfile(): AuthProfile {
  const parsed = keycloak.tokenParsed as
    | {
        preferred_username?: string;
        realm_access?: { roles?: string[] };
        exp?: number;
      }
    | undefined;

  return {
    preferredUsername: parsed?.preferred_username ?? 'authenticated user',
    realmRoles: parsed?.realm_access?.roles ?? keycloak.realmAccess?.roles ?? [],
    tokenExpiresAt: parsed?.exp ? new Date(parsed.exp * 1000).toISOString() : null,
  };
}

export function getKeycloak(): Keycloak {
  return keycloak;
}
