import {
  disposeAuthRefresh,
  getAuthProfile,
  initAuth,
  login,
  logout,
  onAuthEvent,
  type AuthErrorDetails,
} from '../auth/keycloak';

class ScimAppElement extends HTMLElement {
  private bound = false;
  private unsubscribeEvents: Array<() => void> = [];
  private authError: AuthErrorDetails | null = null;

  connectedCallback(): void {
    if (this.bound) {
      return;
    }

    this.bound = true;
    this.bindAuthLifecycleEvents();
    this.renderLoading();
    void this.bootstrap();
  }

  disconnectedCallback(): void {
    this.unsubscribeEvents.forEach((unsubscribe) => unsubscribe());
    this.unsubscribeEvents = [];
    disposeAuthRefresh();
  }

  private bindAuthLifecycleEvents(): void {
    this.unsubscribeEvents.push(
      onAuthEvent('onAuthSuccess', () => {
        this.authError = null;
        this.renderAuthenticated();
        this.bindActions();
      }),
      onAuthEvent('onTokenExpired', () => {
        // Keep the UI responsive while token refresh is attempted in the auth module.
      }),
      onAuthEvent('onAuthLogout', () => {
        this.authError = {
          reason: 'session_timeout',
          message: 'Session timed out. Please sign in again.',
        };
        this.renderError();
        this.bindActions();
      }),
      onAuthEvent('onAuthError', (error) => {
        this.authError = error ?? {
          reason: 'unknown',
          message: 'Authentication failed unexpectedly.',
        };
        this.renderError();
        this.bindActions();
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
        this.bindActions();
        return;
      }

      this.authError = null;
      this.renderAuthenticated();
      this.bindActions();
    } catch (error) {
      this.authError =
        typeof error === 'object' && error !== null && 'reason' in error && 'message' in error
          ? (error as AuthErrorDetails)
          : {
              reason: 'unknown',
              message: error instanceof Error ? error.message : 'Authentication failed unexpectedly.',
            };
      this.renderError();
      this.bindActions();
    }
  }

  private bindActions(): void {
    const logoutButton = this.querySelector<HTMLButtonElement>('[data-action="logout"]');
    const retryButton = this.querySelector<HTMLButtonElement>('[data-action="retry-login"]');

    logoutButton?.addEventListener('click', () => {
      void logout();
    });

    retryButton?.addEventListener('click', () => {
      void login();
    });
  }

  private errorCopy(error: AuthErrorDetails | null): string {
    if (!error) {
      return 'Authentication failed unexpectedly.';
    }

    if (error.reason === 'keycloak_unreachable') {
      return 'Keycloak is unreachable. Verify https://localhost:8443 and local certificates.';
    }

    if (error.reason === 'invalid_client_or_redirect') {
      return 'Invalid client or redirect URI configuration. Verify Keycloak client settings.';
    }

    if (error.reason === 'session_timeout') {
      return 'Session timed out. Sign in again to continue.';
    }

    return error.message;
  }

  private renderLoading(): void {
    this.innerHTML = `
      <main style="font-family: system-ui, sans-serif; max-width: 720px; margin: 48px auto; padding: 0 16px;">
        <h1 style="margin-bottom: 8px;">Hexagonal SCIM</h1>
        <p style="margin-top: 0; color: #4b5563;">Connecting to Keycloak login...</p>
      </main>
    `;
  }

  private renderAuthenticated(): void {
    const profile = getAuthProfile();
    const roles = profile.realmRoles.length > 0 ? profile.realmRoles.join(', ') : 'none';
    const tokenExpiry = profile.tokenExpiresAt ?? 'unknown';

    this.innerHTML = `
      <main style="font-family: system-ui, sans-serif; max-width: 720px; margin: 48px auto; padding: 0 16px;">
        <h1 style="margin-bottom: 8px;">Hexagonal SCIM</h1>
        <p style="margin-top: 0; color: #111827;">
          Signed in as <strong>${profile.preferredUsername}</strong>
        </p>
        <section style="margin: 16px 0; padding: 12px; border: 1px solid #e5e7eb; border-radius: 8px; background: #f9fafb;">
          <p style="margin: 4px 0;"><strong>Realm roles:</strong> ${roles}</p>
          <p style="margin: 4px 0;"><strong>Token expires at:</strong> ${tokenExpiry}</p>
        </section>
        <p style="margin-top: 0; color: #4b5563;">
          First milestone is complete: browser login redirect to local Keycloak works.
        </p>
        <button
          type="button"
          data-action="logout"
          style="padding: 8px 14px; border: 1px solid #d1d5db; border-radius: 8px; background: #ffffff; cursor: pointer;"
        >
          Logout
        </button>
      </main>
    `;
  }

  private renderError(): void {
    this.innerHTML = `
      <main style="font-family: system-ui, sans-serif; max-width: 720px; margin: 48px auto; padding: 0 16px;">
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
}

if (!customElements.get('scim-app')) {
  customElements.define('scim-app', ScimAppElement);
}
