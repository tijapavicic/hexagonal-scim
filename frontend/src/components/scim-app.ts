import {
  disposeAuthRefresh,
  getAuthProfile,
  initAuth,
  login,
  logout,
  onAuthEvent,
  type AuthErrorDetails,
} from '../auth/keycloak';
import {
  SCIM_APP_NOT_FOUND_STYLES,
  SCIM_APP_SHELL_STYLES,
  SCIM_APP_STATUS_STYLES,
} from './scim-app.styles';
import './pages/home-page';
import './pages/users-page';
import './pages/products-page';
import './pages/payments-page';

type Route = '#/home' | '#/users' | '#/products' | '#/payments' | '#/not-found';
const VALID_ROUTES: readonly Route[] = ['#/home', '#/users', '#/products', '#/payments', '#/not-found'];

function normalizeRoute(raw: string): Route {
  return (VALID_ROUTES as readonly string[]).includes(raw) ? (raw as Route) : '#/not-found';
}

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
      case '#/products':
        content.innerHTML = '<products-page></products-page>';
        break;
      case '#/payments':
        content.innerHTML = '<payments-page></payments-page>';
        break;
      case '#/not-found':
        this.renderNotFound(content);
        break;
      case '#/home':
      default: {
        const homePage = document.createElement('home-page') as HTMLElement & { username: string; roles: string[] };
        const profile = getAuthProfile();
        homePage.username = profile.preferredUsername;
        homePage.roles = profile.realmRoles;
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
      await initAuth();

      // If we reach here, authentication was successful (either existing session found or login redirect handled).
      // Note: if no session existed, initAuth() will redirect to login, so this code won't execute until
      // the user returns from Keycloak with an auth code.

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
      <style>${SCIM_APP_SHELL_STYLES}</style>
      <div class="topbar">
        <span class="topbar-brand">Hexagonal SCIM</span>
        <nav>
          <a href="#/home"     data-route="#/home">Home</a>
          <a href="#/users"    data-route="#/users">Users</a>
          <a href="#/products" data-route="#/products">Products</a>
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
      <style>${SCIM_APP_NOT_FOUND_STYLES}</style>
      <div class="nf-wrap">
        <div class="nf-code">404</div>
        <h2 class="nf-title">Page Not Found</h2>
        <p class="nf-text">
          The page you're looking for doesn't exist or has been moved.
        </p>
        <a href="#/home" class="nf-link">&larr; Go to Home</a>
      </div>`;
  }

  private renderLoading(): void {
    this.innerHTML = `
      <style>${SCIM_APP_STATUS_STYLES}</style>
      <main class="status-wrap">
        <h1 class="status-title">Hexagonal SCIM</h1>
        <p class="status-message">Connecting to Keycloak login...</p>
      </main>
    `;
  }

  private renderError(): void {
    this.innerHTML = `
      <style>${SCIM_APP_STATUS_STYLES}</style>
      <main class="status-wrap">
        <h1 class="status-title">Hexagonal SCIM</h1>
        <p class="status-message error">${this.errorCopy(this.authError)}</p>
        <button
          type="button"
          data-action="retry-login"
          class="status-btn"
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
