import { getJson } from '../../api/http';
import { getAllUsersAdminOnly } from '../../api/users';
import { HOME_PAGE_STYLES } from './home-page.styles';

type CardState = 'loading' | 'ready' | 'error';

interface AllUsersResponse {
  id: number;
  email: string;
  displayName: string;
}

class HomePageElement extends HTMLElement {
  static observedAttributes = ['username', 'roles'];

  private usersState: CardState = 'loading';
  private usersCount = 0;
  private paymentsState: CardState = 'loading';
  private paymentsCount = 0;
  private allUsersModalVisible = false;
  private allUsers: AllUsersResponse[] = [];
  private allUsersLoading = false;
  private allUsersError: string | null = null;

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  connectedCallback(): void {
    this.render();
    void this.loadCounts();
  }

  attributeChangedCallback(): void {
    if (this.isConnected) this.render();
  }

  get username(): string {
    return this.getAttribute('username') ?? '';
  }

  set username(value: string) {
    this.setAttribute('username', value);
  }

  get roles(): string[] {
    const rolesAttr = this.getAttribute('roles') ?? '';
    return rolesAttr ? rolesAttr.split(',').map(r => r.trim()) : [];
  }

  set roles(value: string[]) {
    this.setAttribute('roles', value.join(', '));
  }

  private isAdmin(): boolean {
    return this.roles.some(role => role.toLowerCase() === 'admin');
  }

  private async loadCounts(): Promise<void> {
    const [usersResult, paymentsResult] = await Promise.allSettled([
      getJson<unknown[]>('/api/v1/users?page=0&size=10'),
      getJson<unknown[]>('/api/v1/payments?page=0&size=10'),
    ]);

    if (usersResult.status === 'fulfilled') {
      this.usersCount = usersResult.value.length;
      this.usersState = 'ready';
    } else {
      this.usersState = 'error';
    }

    if (paymentsResult.status === 'fulfilled') {
      this.paymentsCount = paymentsResult.value.length;
      this.paymentsState = 'ready';
    } else {
      this.paymentsState = 'error';
    }

    this.render();
  }

  private async fetchAllUsers(): Promise<void> {
    this.allUsersLoading = true;
    this.allUsersError = null;
    this.render();

    try {
      this.allUsers = await getAllUsersAdminOnly();
      this.allUsersLoading = false;
      this.render();
    } catch (error) {
      this.allUsersLoading = false;
      this.allUsersError = error instanceof Error
        ? error.message
        : 'Failed to fetch users. You may not have admin role.';
      this.render();
    }
  }

  private countHtml(state: CardState, count: number): string {
    if (state === 'loading') return '<span class="skeleton"></span>';
    if (state === 'error')   return '<span class="error-chip">Failed to load</span>';
    return String(count);
  }

  private closeModal(): void {
    this.allUsersModalVisible = false;
    this.allUsersError = null;
    this.render();
  }

  private onGetAllUsersClick(): void {
    this.allUsersModalVisible = true;
    this.allUsers = [];
    void this.fetchAllUsers();
  }

  private render(): void {
    const name = this.username || 'there';
    const adminModalHtml = this.renderAdminModal();

    this.shadowRoot!.innerHTML = `
      <style>${HOME_PAGE_STYLES}</style>
      <div class="welcome">
        <h2>Welcome, ${name} 👋</h2>
        <p>Here's a summary of the current state. Use the navigation above to manage data.</p>
      </div>
      <div class="cards">

        <!-- Profile card (static) -->
        <div class="card">
          <div class="card-icon">👤</div>
          <div class="card-label">Signed in as</div>
          <div class="card-value card-value-sm">${name}</div>
        </div>

        <!-- Users summary card -->
        <a href="#/users" class="card link-card">
          <div class="card-icon">👥</div>
          <div class="card-label">Users on this page</div>
          <div class="card-value">${this.countHtml(this.usersState, this.usersCount)}</div>
          <div class="card-link">View all →</div>
        </a>

        <!-- Payments summary card -->
        <a href="#/payments" class="card link-card">
          <div class="card-icon">💳</div>
          <div class="card-label">Payments on this page</div>
          <div class="card-value">${this.countHtml(this.paymentsState, this.paymentsCount)}</div>
          <div class="card-link">View all →</div>
        </a>

        ${this.isAdmin() ? `
        <!-- Admin: Get all users card -->
        <div class="card admin-card">
          <div class="card-icon">🔐</div>
          <div class="card-label">Admin Panel</div>
          <div class="card-value card-value-sm">Get all users</div>
          <button class="admin-btn" data-action="get-all-users">Fetch All Users →</button>
        </div>
        ` : ''}

      </div>

      ${adminModalHtml}
    `;

    // Attach event listeners
    this.shadowRoot!.querySelector('[data-action="get-all-users"]')?.addEventListener('click', () => {
      this.onGetAllUsersClick();
    });

    this.shadowRoot!.querySelector('[data-action="close-modal"]')?.addEventListener('click', () => {
      this.closeModal();
    });

    // Close modal when clicking outside
    this.shadowRoot!.querySelector('.modal')?.addEventListener('click', (e) => {
      if ((e.target as HTMLElement).classList.contains('modal')) {
        this.closeModal();
      }
    });
  }

  private renderAdminModal(): string {
    if (!this.isAdmin()) return '';

    if (!this.allUsersModalVisible) return '';

    let content = '';
    if (this.allUsersLoading) {
      content = '<div class="modal-loading">Loading users...</div>';
    } else if (this.allUsersError) {
      content = `<div class="modal-error">❌ ${this.allUsersError}</div>`;
    } else if (this.allUsers.length === 0) {
      content = '<div class="modal-empty">No users found</div>';
    } else {
      content = `
        <div class="modal-header">All Users (${this.allUsers.length})</div>
        <div class="modal-users-list">
          ${this.allUsers.map(user => `
            <div class="user-item">
              <div class="user-name">${this.escapeHtml(user.displayName)}</div>
              <div class="user-email">${this.escapeHtml(user.email)}</div>
            </div>
          `).join('')}
        </div>
      `;
    }

    return `
      <div class="modal">
        <div class="modal-content">
          <button class="modal-close" data-action="close-modal">✕</button>
          ${content}
        </div>
      </div>
    `;
  }

  private escapeHtml(text: string): string {
    const map: { [key: string]: string } = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
    return text.replace(/[&<>"']/g, (c) => map[c]);
  }
}

if (!customElements.get('home-page')) {
  customElements.define('home-page', HomePageElement);
}

export { HomePageElement };

