import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const initAuthMock = vi.fn();
const getAuthProfileMock = vi.fn();
const loginMock = vi.fn().mockResolvedValue(undefined);
const logoutMock = vi.fn().mockResolvedValue(undefined);
const onAuthEventMock = vi.fn(() => () => undefined);
const disposeAuthRefreshMock = vi.fn();
const fetchUsersPageMock = vi.fn();

vi.mock('../auth/keycloak', () => ({
  initAuth: initAuthMock,
  getAuthProfile: getAuthProfileMock,
  login: loginMock,
  logout: logoutMock,
  onAuthEvent: onAuthEventMock,
  disposeAuthRefresh: disposeAuthRefreshMock,
}));

vi.mock('../api/user-list', () => ({
  fetchUsersPage: fetchUsersPageMock,
}));

async function flushPromises(): Promise<void> {
  await Promise.resolve();
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('components/scim-app', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.clearAllMocks();
    getAuthProfileMock.mockReturnValue({
      preferredUsername: 'testuser',
      realmRoles: ['ROLE_USER'],
      tokenExpiresAt: '2026-05-15T21:00:00.000Z',
    });
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('renders loading state while auth bootstrap is pending', async () => {
    initAuthMock.mockImplementation(
      () =>
        new Promise<boolean>(() => {
          // Intentionally pending to assert transient loading state.
        }),
    );
    fetchUsersPageMock.mockResolvedValue({ content: [] });

    await import('./scim-app');

    const element = document.createElement('scim-app');
    document.body.appendChild(element);

    expect(document.body.textContent).toContain('Connecting to Keycloak login...');
  });

  it('renders authenticated state and users table after successful bootstrap', async () => {
    initAuthMock.mockResolvedValue(true);
    fetchUsersPageMock.mockResolvedValue({
      content: [
        { id: 1, displayName: 'Alice', email: 'alice@example.com' },
        { id: 2, displayName: 'Bob', email: 'bob@example.com' },
      ],
    });

    await import('./scim-app');

    const element = document.createElement('scim-app');
    document.body.appendChild(element);
    await flushPromises();

    expect(document.body.textContent).toContain('Signed in as');
    expect(document.body.textContent).toContain('testuser');
    expect(document.body.textContent).toContain('Users');
    expect(document.body.textContent).toContain('Alice');
    expect(document.body.textContent).toContain('bob@example.com');
  });

  it('renders explicit error state when auth bootstrap fails', async () => {
    initAuthMock.mockRejectedValue({
      reason: 'invalid_client_or_redirect',
      message: 'Invalid Keycloak client or redirect URI configuration.',
    });

    await import('./scim-app');

    const element = document.createElement('scim-app');
    document.body.appendChild(element);
    await flushPromises();

    expect(document.body.textContent).toContain('Invalid client or redirect URI configuration');
    expect(document.body.textContent).toContain('Sign in again');
  });
});

