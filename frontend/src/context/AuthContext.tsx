import {
  createContext,
  useContext,
  useState,
  useEffect,
  useRef,
  type ReactNode,
} from 'react';
import type Keycloak from 'keycloak-js';
import kc from '../keycloak';

interface AuthContextType {
  keycloak: Keycloak;
  authenticated: boolean;
  username: string;
  roles: string[];
  /** Returns the current bearer token string */
  token: () => string;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

/**
 * Initialises Keycloak with login-required + PKCE (S256) before rendering children.
 * Refreshes the token automatically every 60 s if it expires within 30 s.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const initialized = useRef(false);

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;

    kc.init({ onLoad: 'login-required', pkceMethod: 'S256' })
      .then((auth) => {
        setAuthenticated(auth);
        setReady(true);

        // Proactive token refresh
        const id = setInterval(() => {
          kc.updateToken(30).catch(() => kc.logout());
        }, 60_000);

        return () => clearInterval(id);
      })
      .catch((err) => {
        console.error('Keycloak init error', err);
        setReady(true);
      });
  }, []);

  if (!ready) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-gray-950">
        <div className="animate-spin h-10 w-10 rounded-full border-4 border-indigo-600 border-t-transparent mb-4" />
        <p className="text-gray-500 text-sm">Connecting to identity provider…</p>
      </div>
    );
  }

  const roles: string[] = kc.realmAccess?.roles ?? [];
  const username = (kc.tokenParsed?.preferred_username as string | undefined) ?? '';

  const value: AuthContextType = {
    keycloak: kc,
    authenticated,
    username,
    roles,
    token: () => kc.token ?? '',
    logout: () => kc.logout({ redirectUri: window.location.origin }),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>');
  return ctx;
}

