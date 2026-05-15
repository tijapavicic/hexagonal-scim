/**
 * <notification-bar> — fixed top-right toast.
 *
 * Usage:
 *   const bar = document.querySelector('notification-bar') as NotificationBarElement;
 *   bar.show('User created.', 'success');
 *   bar.show('Request failed.', 'error');
 */
export class NotificationBarElement extends HTMLElement {
  private dismissTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  connectedCallback(): void {
    this.renderHidden();
  }

  show(message: string, type: 'success' | 'error' = 'success'): void {
    if (this.dismissTimer !== null) clearTimeout(this.dismissTimer);

    this.shadowRoot!.innerHTML = `
      <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
        :host {
          position: fixed;
          top: 20px;
          right: 20px;
          z-index: 9999;
          font-family: system-ui, sans-serif;
        }
        .toast {
          padding: 12px 20px;
          border-radius: 8px;
          font-size: 14px;
          font-weight: 500;
          box-shadow: 0 4px 16px rgba(0,0,0,0.15);
          animation: slide-in 0.2s ease;
        }
        @keyframes slide-in {
          from { transform: translateX(100%); opacity: 0; }
          to   { transform: translateX(0);    opacity: 1; }
        }
        .success { background: #d1fae5; color: #065f46; border: 1px solid #6ee7b7; }
        .error   { background: #fee2e2; color: #991b1b; border: 1px solid #fca5a5; }
      </style>
      <div class="toast ${type}" role="alert">${message}</div>`;

    this.dismissTimer = setTimeout(() => this.renderHidden(), 4000);
  }

  private renderHidden(): void {
    this.shadowRoot!.innerHTML = `
      <style>:host { position: fixed; top: 20px; right: 20px; z-index: 9999; pointer-events: none; }</style>`;
  }
}

if (!customElements.get('notification-bar')) {
  customElements.define('notification-bar', NotificationBarElement);
}

