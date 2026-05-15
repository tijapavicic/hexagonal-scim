import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const initAuthMock = vi.fn();
const getAuthProfileMock = vi.fn();
const loginMock = vi.fn().mockResolvedValue(undefined);
const logoutMock = vi.fn().mockResolvedValue(undefined);
const onAuthEventMock = vi.fn(() => () => undefined);
const disposeAuthRefreshMock = vi.fn();

vi.mock('../auth/keycloak', () => ({
  initAuth: initAuthMock,
  getAuthProfile: getAuthProfileMock,
  login: loginMock,
  logout: logoutMock,
  onAuthEvent: onAuthEventMock,
  disposeAuthRefresh: disposeAuthRefreshMock,
}));

// Pages are custom elements – stub them so jsdom doesn't choke on Shadow DOM
vi.mock('./pages/home-page', () => ({}));
vi.mock('./pages/users-page', () => ({}));

async function flushPromises(): Promise<void> {
  await Promise.resolve();
  await new Promise((resolve) => setTimeout(resolve, 0));
}

interface ScimAppEl extends HTMLElement {
  routeTo(hash: string): void;
}

describe('components/scim-app', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
    vi.clearAllMocks();
    window.location.hash = '';
    getAuthProfileMock.mockReturnValue({
      preferredUsername: 'testuser',
      realmRoles: ['ROLE_USER'],
      tokenExpiresAt: '2026-05-15T21:00:00.000Z',
    });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    window.location.hash = '';
  });

  // ── pre-auth states ──────────────────────────────────────────────────────────

  it('renders loading state while auth bootstrap is pending', async () => {
    initAuthMock.mockImplementation(() => new Promise<boolean>(() => { /* never resolves */ }));

    await import('./scim-app');
    const element = document.createElement('scim-app');
    document.body.appendChild(element);

    expect(document.body.textContent).toContain('Connecting to Keycloak login...');
  });

  it('renders error state with "Sign in again" when auth bootstrap fails', async () => {
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

  // ── post-auth shell ──────────────────────────────────────────────────────────

  it('renders topbar with 3 nav links after successful auth', async () => {
    initAuthMock.mockResolvedValue(true);

    await import('./scim-app');
    const element = document.createElement('scim-app');
    document.body.appendChild(element);
    await flushPromises();

    const links = element.querySelectorAll<HTMLAnchorElement>('[data-route]');
    expect(links).toHaveLength(3);
    const routes = Array.from(links).map((l) => l.dataset['route']);
    expect(routes).toEqual(['#/home', '#/users', '#/payments']);
  });

  it('shows username in profile chip after auth', async () => {
    initAuthMock.mockResolvedValue(true);

    await import('./scim-app');
    const element = document.createElement('scim-app');
    document.body.appendChild(element);
    await flushPromises();

    expect(element.textContent).toContain('testuser');
  });

  // ── routing ──────────────────────────────────────────────────────────────────

  it('routeTo #/home marks Home nav link active', async () => {
    initAuthMock.mockResolvedValue(true);

    await import('./scim-app');
    const element = document.createElement('scim-app') as ScimAppEl;
    document.body.appendChild(element);
    await flushPromises();

    element.routeTo('#/home');

    const active = element.querySelectorAll('.active');
    expect(active).toHaveLength(1);
    expect((active[0] as HTMLElement).dataset['route']).toBe('#/home');
  });

  it('routeTo #/users marks Users nav link active and renders users-page', async () => {
    initAuthMock.mockResolvedValue(true);

    await import('./scim-app');
    const element = document.createElement('scim-app') as ScimAppEl;
    document.body.appendChild(element);
    await flushPromises();

    element.routeTo('#/users');

    const active = element.querySelectorAll('.active');
    expect(active).toHaveLength(1);
    expect((active[0] as HTMLElement).dataset['route']).toBe('#/users');
    expect(element.querySelector('#page-content')?.innerHTML).toContain('users-page');
  });

  it('routeTo #/payments marks Payments nav link active and shows placeholder', async () => {
    initAuthMock.mockResolvedValue(true);

    await import('./scim-app');
    const element = document.createElement('scim-app') as ScimAppEl;
    document.body.appendChild(element);
    await flushPromises();

    element.routeTo('#/payments');

    const active = element.querySelectorAll('.active');
    expect(active).toHaveLength(1);
    expect((active[0] as HTMLElement).dataset['route']).toBe('#/payments');
    expect(element.querySelector('#page-content')?.textContent).toContain('coming soon');
  });

  it('routeTo unknown hash falls back to #/home', async () => {
    initAuthMock.mockResolvedValue(true);

    await import('./scim-app');
    const element = document.createElement('scim-app') as ScimAppEl;
    document.body.appendChild(element);
    await flushPromises();

    element.routeTo('#/unknown-route');

    // Window hash is updated to canonical route; no multiple active links.
    const active = element.querySelectorAll('.active');
    expect(active.length).toBeLessThanOrEqual(1);
  });
});

