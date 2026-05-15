import {
  NOTIFICATION_BAR_HIDDEN_STYLES,
  NOTIFICATION_BAR_VISIBLE_STYLES,
} from './notification-bar.styles';

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
      <style>${NOTIFICATION_BAR_VISIBLE_STYLES}</style>
      <div class="toast ${type}" role="alert">${message}</div>`;

    this.dismissTimer = setTimeout(() => this.renderHidden(), 4000);
  }

  private renderHidden(): void {
    this.shadowRoot!.innerHTML = `
      <style>${NOTIFICATION_BAR_HIDDEN_STYLES}</style>`;
  }
}

if (!customElements.get('notification-bar')) {
  customElements.define('notification-bar', NotificationBarElement);
}

