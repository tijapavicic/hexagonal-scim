import { fetchUsersPage } from '../../api/user-list';
import type { UserListItemDto } from '../../types/user-list.dto';
import { login } from '../../auth/keycloak';

const STYLES = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :host { display: block; font-family: system-ui, sans-serif; }
  h2 { font-size: 18px; font-weight: 600; color: #111827; margin-bottom: 16px; }
  .card {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 16px;
  }
  .loading { color: #4b5563; }
  .error-text { color: #b91c1c; margin-bottom: 8px; }
  .empty { color: #4b5563; }
  button {
    padding: 8px 14px;
    border: 1px solid #d1d5db;
    border-radius: 6px;
    background: #fff;
    cursor: pointer;
    font-size: 14px;
  }
  button:hover { background: #f9fafb; }
  table { width: 100%; border-collapse: collapse; font-size: 14px; }
  thead tr { background: #f9fafb; text-align: left; }
  th { padding: 8px 12px; border-bottom: 2px solid #e5e7eb; font-weight: 600; color: #374151; }
  td { padding: 8px 12px; border-bottom: 1px solid #e5e7eb; color: #111827; }
  tr:last-child td { border-bottom: none; }
`;

class UsersPageElement extends HTMLElement {
  private users: UserListItemDto[] = [];
  private loading = false;
  private error: string | null = null;

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  connectedCallback(): void {
    this.render();
    void this.loadUsers();
  }

  private async loadUsers(): Promise<void> {
    this.loading = true;
    this.error = null;
    this.render();

    try {
      const page = await fetchUsersPage();
      this.users = page.content;
    } catch (err) {
      this.users = [];
      this.error = err instanceof Error ? err.message : 'Unable to load users.';
    } finally {
      this.loading = false;
      this.render();
      this.bindActions();
    }
  }

  private bindActions(): void {
    this.shadowRoot
      ?.querySelector<HTMLButtonElement>('[data-action="retry"]')
      ?.addEventListener('click', () => void this.loadUsers());
  }

  private tableBody(): string {
    return this.users
      .map(
        (u) => `
        <tr>
          <td>${u.id}</td>
          <td>${u.displayName}</td>
          <td>${u.email}</td>
        </tr>`,
      )
      .join('');
  }

  private content(): string {
    if (this.loading) {
      return '<p class="loading">Loading users…</p>';
    }
    if (this.error) {
      return `
        <p class="error-text">${this.error}</p>
        <button type="button" data-action="retry">Retry</button>`;
    }
    if (this.users.length === 0) {
      return '<p class="empty">No users found.</p>';
    }
    return `
      <div style="overflow-x: auto;">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Display name</th>
              <th>Email</th>
            </tr>
          </thead>
          <tbody>${this.tableBody()}</tbody>
        </table>
      </div>`;
  }

  private render(): void {
    this.shadowRoot!.innerHTML = `
      <style>${STYLES}</style>
      <div class="card">
        <h2>Users</h2>
        ${this.content()}
      </div>`;
  }
}

if (!customElements.get('users-page')) {
  customElements.define('users-page', UsersPageElement);
}

export { UsersPageElement };
// Re-export login so tests can mock it without touching the auth module directly.
export { login };

