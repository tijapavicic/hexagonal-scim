import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

type MockKeycloakInstance = {
  init: ReturnType<typeof vi.fn>;
  updateToken: ReturnType<typeof vi.fn>;
  login: ReturnType<typeof vi.fn>;
  logout: ReturnType<typeof vi.fn>;
  token: string | null;
  tokenParsed?: { exp?: number; preferred_username?: string; realm_access?: { roles?: string[] } };
  realmAccess?: { roles?: string[] };
  onAuthSuccess?: () => void;
  onTokenExpired?: () => void;
  onAuthLogout?: () => void;
  onAuthError?: (errorData: unknown) => void;
};

const keycloakInstances: MockKeycloakInstance[] = [];

vi.mock('keycloak-js', () => {
  return {
    default: vi.fn(() => {
      const instance: MockKeycloakInstance = {
        init: vi.fn().mockResolvedValue(true),
        updateToken: vi.fn().mockResolvedValue(true),
        login: vi.fn().mockResolvedValue(undefined),
        logout: vi.fn().mockResolvedValue(undefined),
        token: 'fake-token',
      };
      keycloakInstances.push(instance);
      return instance;
    }),
  };
});

async function loadAuthModule() {
  vi.resetModules();
  keycloakInstances.length = 0;
  const mod = await import('./keycloak');
  const keycloak = mod.getKeycloak() as unknown as MockKeycloakInstance;
  return { ...mod, keycloak };
}

describe('auth/keycloak', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('initializes auth exactly once for repeated initAuth calls', async () => {
    const { initAuth, keycloak } = await loadAuthModule();

    const first = await initAuth();
    const second = await initAuth();

    expect(first).toBe(true);
    expect(second).toBe(true);
    expect(keycloak.init).toHaveBeenCalledTimes(1);
  });

  it('runs the refresh path and calls updateToken on interval', async () => {
    const { initAuth, keycloak, disposeAuthRefresh } = await loadAuthModule();

    await initAuth();
    expect(keycloak.updateToken).toHaveBeenCalledTimes(0);

    vi.advanceTimersByTime(60_000);
    await Promise.resolve();

    expect(keycloak.updateToken).toHaveBeenCalledTimes(1);

    disposeAuthRefresh();
    vi.advanceTimersByTime(60_000);
    await Promise.resolve();

    expect(keycloak.updateToken).toHaveBeenCalledTimes(1);
  });

  it('uses deterministic redirect URI during logout', async () => {
    const { logout, keycloak } = await loadAuthModule();

    await logout();

    expect(keycloak.logout).toHaveBeenCalledTimes(1);
    expect(keycloak.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
  });
});

