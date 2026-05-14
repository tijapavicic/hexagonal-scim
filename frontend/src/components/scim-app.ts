import { disposeAuthRefresh, getKeycloak, initAuth } from '../auth/keycloak';

class ScimAppElement extends HTMLElement {
  private bound = false;

  connectedCallback(): void {
    if (this.bound) {
      return;
    }

    this.bound = true;
    this.renderLoading();
    void this.bootstrap();
  }

  disconnectedCallback(): void {
    disposeAuthRefresh();
  }

  private async bootstrap(): Promise<void> {
    try {
      const authenticated = await initAuth();

      if (!authenticated) {
        this.renderError('Authentication was not completed.');
        return;
      }

      this.renderAuthenticated();
      this.bindEvents();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unexpected error';
      this.renderError(`Unable to initialize login: ${message}`);
    }
  }

  private bindEvents(): void {
    const keycloak = getKeycloak();
    const logoutButton = this.querySelector<HTMLButtonElement>('[data-action="logout"]');

    logoutButton?.addEventListener('click', () => {
      keycloak.logout({ redirectUri: window.location.origin });
    });
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
    const keycloak = getKeycloak();
    const username = (keycloak.tokenParsed?.preferred_username as string | undefined) ?? 'authenticated user';

    this.innerHTML = `
      <main style="font-family: system-ui, sans-serif; max-width: 720px; margin: 48px auto; padding: 0 16px;">
        <h1 style="margin-bottom: 8px;">Hexagonal SCIM</h1>
        <p style="margin-top: 0; color: #111827;">
          Signed in as <strong>${username}</strong>
        </p>
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

  private renderError(message: string): void {
    this.innerHTML = `
      <main style="font-family: system-ui, sans-serif; max-width: 720px; margin: 48px auto; padding: 0 16px;">
        <h1 style="margin-bottom: 8px;">Hexagonal SCIM</h1>
        <p style="margin-top: 0; color: #b91c1c;">${message}</p>
      </main>
    `;
  }
}

if (!customElements.get('scim-app')) {
  customElements.define('scim-app', ScimAppElement);
}
