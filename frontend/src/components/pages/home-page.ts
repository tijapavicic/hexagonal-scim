import { getJson } from '../../api/http';
import { HOME_PAGE_STYLES } from './home-page.styles';

type CardState = 'loading' | 'ready' | 'error';

class HomePageElement extends HTMLElement {
  static observedAttributes = ['username'];

  private usersState: CardState = 'loading';
  private usersCount = 0;
  private paymentsState: CardState = 'loading';
  private paymentsCount = 0;

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

  private countHtml(state: CardState, count: number): string {
    if (state === 'loading') return '<span class="skeleton"></span>';
    if (state === 'error')   return '<span class="error-chip">Failed to load</span>';
    return String(count);
  }

  private render(): void {
    const name = this.username || 'there';
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

      </div>`;
  }
}

if (!customElements.get('home-page')) {
  customElements.define('home-page', HomePageElement);
}

export { HomePageElement };

