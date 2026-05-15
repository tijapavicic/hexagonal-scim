import { getJson } from '../../api/http';

type CardState = 'loading' | 'ready' | 'error';

const STYLES = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :host { display: block; font-family: system-ui, sans-serif; }

  .welcome {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 24px;
    margin-bottom: 20px;
  }
  .welcome h2 { font-size: 22px; font-weight: 700; color: #111827; margin-bottom: 6px; }
  .welcome p  { color: #4b5563; font-size: 14px; }

  .cards { display: flex; gap: 16px; flex-wrap: wrap; }

  .card {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 20px;
    min-width: 200px;
    flex: 1;
  }
  .card.link-card {
    text-decoration: none;
    color: inherit;
    cursor: pointer;
    transition: box-shadow 0.15s, transform 0.12s;
    display: block;
  }
  .card.link-card:hover {
    box-shadow: 0 4px 14px rgba(0,0,0,0.1);
    transform: translateY(-2px);
  }

  .card-icon  { font-size: 28px; margin-bottom: 10px; }
  .card-label { font-size: 12px; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 4px; }
  .card-value { font-size: 28px; font-weight: 700; color: #111827; margin-bottom: 8px; }
  .card-link  { font-size: 13px; color: #4f46e5; font-weight: 600; }

  /* loading skeleton */
  .skeleton {
    display: inline-block;
    width: 48px;
    height: 28px;
    border-radius: 4px;
    background: linear-gradient(90deg, #e5e7eb 25%, #f3f4f6 50%, #e5e7eb 75%);
    background-size: 200% 100%;
    animation: shimmer 1.2s infinite;
    vertical-align: middle;
  }
  @keyframes shimmer { 0% { background-position: -200% 0; } 100% { background-position: 200% 0; } }

  .error-chip {
    font-size: 13px;
    color: #b91c1c;
    font-weight: 500;
  }
`;

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
      <style>${STYLES}</style>
      <div class="welcome">
        <h2>Welcome, ${name} 👋</h2>
        <p>Here's a summary of the current state. Use the navigation above to manage data.</p>
      </div>
      <div class="cards">

        <!-- Profile card (static) -->
        <div class="card">
          <div class="card-icon">👤</div>
          <div class="card-label">Signed in as</div>
          <div class="card-value" style="font-size:18px;">${name}</div>
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

