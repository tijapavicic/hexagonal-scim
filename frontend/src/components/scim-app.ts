import {
  disposeAuthRefresh,
  getAuthProfile,
  initAuth,
  login,
  logout,
  onAuthEvent,
  type AuthErrorDetails,
} from '../auth/keycloak';
import './pages/home-page';
import './pages/users-page';
import './pages/payments-page';

type Route = '#/home' | '#/users' | '#/payments' | '#/not-found';
const VALID_ROUTES: readonly Route[] = ['#/home', '#/users', '#/payments', '#/not-found'];

function normalizeRoute(raw: string): Route {
  return (VALID_ROUTES as readonly string[]).includes(raw) ? (raw as Route) : '#/not-found';
}

const SHELL_STYLES = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :host { display: block; min-height: 100vh; font-family: system-ui, sans-serif; }
  .topbar {
    background: #1a1a2e;
    color: #fff;
    display: flex;
    align-items: center;
    padding: 0 24px;
    height: 56px;
    gap: 16px;
    position: sticky;
    top: 0;
    z-index: 10;
  }
  .topbar-brand { font-size: 17px; font-weight: 700; margin-right: 8px; white-space: nowrap; }
  nav { display: flex; gap: 4px; flex: 1; }
  nav a {
    color: rgba(255,255,255,0.75);
    text-decoration: none;
    padding: 6px 14px;
    border-radius: 6px;
    font-size: 14px;
    transition: background 0.15s, color 0.15s;
  }
  nav a:hover { background: rgba(255,255,255,0.1); color: #fff; }
  nav a.active { background: #fff; color: #1a1a2e; font-weight: 600; }
  nav a:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
  .profile-chip {
    font-size: 12px;
    color: rgba(255,255,255,0.85);
    white-space: nowrap;
    flex-shrink: 0;
  }
  .logout-btn {
    padding: 6px 14px;
    border: 1px solid rgba(255,255,255,0.35);
    border-radius: 6px;
    background: transparent;
    color: #fff;
    cursor: pointer;
    font-size: 13px;
    flex-shrink: 0;
    transition: background 0.15s;
  }
  .logout-btn:hover { background: rgba(255,255,255,0.12); }
  .logout-btn:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
  #page-content { padding: 24px; background: #f8f9fa; min-height: calc(100vh - 56px); }
`;

class ScimAppElement extends HTMLElement {
  private bound = false;
  private unsubscribeEvents: Array<() => void> = [];
  private authError: AuthErrorDetails | null = null;
  private countdownTimer: number | null = null;

  private readonly onHashChange = (): void => {
    this.routeTo(window.location.hash || '#/home');
  };

  connectedCallback(): void {
    if (this.bound) return;
    this.bound = true;
    this.bindAuthLifecycleEvents();
    this.renderLoading();
    void this.bootstrap();
  }

  disconnectedCallback(): void {
    this.unsubscribeEvents.forEach((fn) => fn());
    this.unsubscribeEvents = [];
    window.removeEventListener('hashchange', this.onHashChange);
    disposeAuthRefresh();
    this.stopCountdown();
  }

  /** Public so tests can drive navigation directly. */
  routeTo(hash: string): void {
    const route = normalizeRoute(hash);

    // Redirect non-canonical hashes so the URL stays clean.
    if (hash && hash !== route) {
      window.location.hash = route;
      return;
    }

    // Highlight active nav link (not-found intentionally matches no nav item).
    this.querySelectorAll<HTMLAnchorElement>('[data-route]').forEach((link) => {
      link.classList.toggle('active', link.dataset['route'] === route);
    });

    const content = this.querySelector<HTMLElement>('#page-content');
    if (!content) return;

    switch (route) {
      case '#/users':
        content.innerHTML = '<users-page></users-page>';
        break;
      case '#/payments':
        content.innerHTML = '<payments-page></payments-page>';
        break;
      case '#/not-found':
        this.renderNotFound(content);
        break;
      case '#/home':
      default: {
        const homePage = document.createElement('home-page') as HTMLElement & { username: string };
        homePage.username = getAuthProfile().preferredUsername;
        content.replaceChildren(homePage);
        break;
      }
    }
  }

  private bindAuthLifecycleEvents(): void {
    this.unsubscribeEvents.push(
      onAuthEvent('onTokenExpired', () => {
        // Token refresh is handled inside the auth module; no shell UI change needed.
      }),
      onAuthEvent('onAuthLogout', () => {
        this.authError = {
          reason: 'session_timeout',
          message: 'Session timed out. Please sign in again.',
        };
        window.removeEventListener('hashchange', this.onHashChange);
        this.stopCountdown();
        this.renderError();
        this.bindErrorActions();
      }),
      onAuthEvent('onAuthError', (error) => {
        this.authError = error ?? {
          reason: 'unknown',
          message: 'Authentication failed unexpectedly.',
        };
        window.removeEventListener('hashchange', this.onHashChange);
        this.stopCountdown();
        this.renderError();
        this.bindErrorActions();
      }),
    );
  }

  private async bootstrap(): Promise<void> {
    try {
      const authenticated = await initAuth();

      if (!authenticated) {
        this.authError = {
          reason: 'session_timeout',
          message: 'Session timed out. Please sign in again.',
        };
        this.renderError();
        this.bindErrorActions();
        return;
      }

      this.authError = null;
      this.renderShell();
      this.startCountdown();
      window.addEventListener('hashchange', this.onHashChange);
      this.routeTo(window.location.hash || '#/home');
    } catch (error) {
      this.authError =
        typeof error === 'object' && error !== null && 'reason' in error && 'message' in error
          ? (error as AuthErrorDetails)
          : {
              reason: 'unknown',
              message: error instanceof Error ? error.message : 'Authentication failed unexpectedly.',
            };
      this.renderError();
      this.bindErrorActions();
    }
  }

  private renderShell(): void {
    const profile = getAuthProfile();

    this.innerHTML = `
      <style>${SHELL_STYLES}</style>
      <div class="topbar">
        <span class="topbar-brand">Hexagonal SCIM</span>
        <nav>
          <a href="#/home"     data-route="#/home">Home</a>
          <a href="#/users"    data-route="#/users">Users</a>
          <a href="#/payments" data-route="#/payments">Payments</a>
        </nav>
        <span class="profile-chip" id="profile-chip">${this.profileChipText(profile.preferredUsername)}</span>
        <button type="button" class="logout-btn" data-action="logout">Logout</button>
      </div>
      <div id="page-content"></div>
    `;

    this.querySelector('[data-action="logout"]')?.addEventListener('click', () => void logout());
  }

  // ── token expiry countdown ─────────────────────────────────────────────────

  private profileChipText(username?: string): string {
    const profile = getAuthProfile();
    const name = username ?? profile.preferredUsername;
    const roles = profile.realmRoles.join(', ') || 'none';
    const expiry = this.formatExpiry(profile.tokenExpiresAt);
    return `\u{1F464} ${name} \u00B7 ${roles} \u00B7 ${expiry}`;
  }

  private formatExpiry(iso: string | null): string {
    if (!iso) return '\u2013';
    const mins = Math.ceil((new Date(iso).getTime() - Date.now()) / 60_000);
    if (mins <= 0) return 'expired';
    return `\u23F1 ${mins}m`;
  }

  private startCountdown(): void {
    const tick = (): void => {
      const chip = this.querySelector<HTMLElement>('#profile-chip');
      if (chip) chip.textContent = this.profileChipText();
    };
    // Refresh every 60 s so the countdown stays accurate.
    this.countdownTimer = window.setInterval(tick, 60_000);
  }

  private stopCountdown(): void {
    if (this.countdownTimer !== null) {
      window.clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  }

  // ── page renderers ─────────────────────────────────────────────────────────

  private renderNotFound(container: HTMLElement): void {
    container.innerHTML = `
      <div style="
        font-family:system-ui,sans-serif;
        max-width:520px;margin:72px auto;padding:0 16px;text-align:center;
      ">
        <div style="font-size:64px;margin-bottom:16px;">404</div>
        <h2 style="font-size:22px;font-weight:700;color:#111827;margin-bottom:8px;">
          Page Not Found
        </h2>
        <p style="color:#6b7280;font-size:14px;margin-bottom:24px;">
          The page you're looking for doesn't exist or has been moved.
        </p>
        <a href="#/home"
           style="
             display:inline-block;padding:10px 20px;
             background:#4338ca;color:#fff;border-radius:6px;
             text-decoration:none;font-size:14px;font-weight:600;
           "
        >\u2190 Go to Home</a>
      </div>`;
  }

  private renderLoading(): void {
    this.innerHTML = `
      <main style="font-family: system-ui, sans-serif; max-width: 900px; margin: 48px auto; padding: 0 16px;">
        <h1 style="margin-bottom: 8px;">Hexagonal SCIM</h1>
        <p style="margin-top: 0; color: #4b5563;">Connecting to Keycloak login...</p>
      </main>
    `;
  }

  private renderError(): void {
    this.innerHTML = `
      <main style="font-family: system-ui, sans-serif; max-width: 900px; margin: 48px auto; padding: 0 16px;">
        <h1 style="margin-bottom: 8px;">Hexagonal SCIM</h1>
        <p style="margin-top: 0; color: #b91c1c;">${this.errorCopy(this.authError)}</p>
        <button
          type="button"
          data-action="retry-login"
          style="padding: 8px 14px; border: 1px solid #d1d5db; border-radius: 8px; background: #ffffff; cursor: pointer;"
        >
          Sign in again
        </button>
      </main>
    `;
  }

  private bindErrorActions(): void {
    this.querySelector<HTMLButtonElement>('[data-action="retry-login"]')?.addEventListener('click', () => {
      void login();
    });
  }

  private errorCopy(error: AuthErrorDetails | null): string {
    if (!error) return 'Authentication failed unexpectedly.';
    if (error.reason === 'keycloak_unreachable')
      return 'Keycloak is unreachable. Verify https://localhost:8443 and local certificates.';
    if (error.reason === 'invalid_client_or_redirect')
      return 'Invalid client or redirect URI configuration. Verify Keycloak client settings.';
    if (error.reason === 'session_timeout') return 'Session timed out. Sign in again to continue.';
    return error.message;
  }
}

if (!customElements.get('scim-app')) {
  customElements.define('scim-app', ScimAppElement);
}
