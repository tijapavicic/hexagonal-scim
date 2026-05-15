const STYLES = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :host { display: block; font-family: system-ui, sans-serif; }
  .welcome {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 24px;
    margin-bottom: 16px;
  }
  .welcome h2 { font-size: 22px; font-weight: 700; color: #111827; margin-bottom: 6px; }
  .welcome p  { color: #4b5563; font-size: 14px; }
  .cards { display: flex; gap: 12px; flex-wrap: wrap; }
  .card {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 20px 24px;
    text-decoration: none;
    color: #1a1a2e;
    font-weight: 600;
    font-size: 15px;
    min-width: 160px;
    transition: box-shadow 0.15s, transform 0.1s;
    display: inline-block;
  }
  .card:hover {
    box-shadow: 0 4px 12px rgba(0,0,0,0.12);
    transform: translateY(-1px);
  }
  .card span { font-size: 24px; display: block; margin-bottom: 8px; }
`;

class HomePageElement extends HTMLElement {
  static observedAttributes = ['username'];

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  connectedCallback(): void {
    this.render();
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

  private render(): void {
    const name = this.username || 'there';
    this.shadowRoot!.innerHTML = `
      <style>${STYLES}</style>
      <div class="welcome">
        <h2>Welcome, ${name} 👋</h2>
        <p>Select a section from the navigation above to get started.</p>
      </div>
      <div class="cards">
        <a href="#/users" class="card">
          <span>👥</span>Users
        </a>
        <a href="#/payments" class="card">
          <span>💳</span>Payments
        </a>
      </div>`;
  }
}

if (!customElements.get('home-page')) {
  customElements.define('home-page', HomePageElement);
}

export { HomePageElement };

