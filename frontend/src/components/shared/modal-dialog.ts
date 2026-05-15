import { MODAL_DIALOG_STYLES } from './modal-dialog.styles';

/**
 * <modal-dialog> — accessible modal with slot-based body.
 *
 * Attributes:
 *   open           – present = visible
 *   title          – heading text
 *   confirm-label  – label for the primary action button (default: "Save")
 *
 * Events (bubbles + composed):
 *   dialog-confirm – user clicked the primary action button; modal stays open
 *                    so the parent can validate/submit before closing.
 *   dialog-cancel  – user dismissed (Cancel, ✕, ESC, backdrop click);
 *                    modal removes "open" attribute automatically.
 *
 * Keyboard:
 *   ESC           – cancel and close
 *   Tab / Shift+Tab – focus trapped inside the modal while open
 */
export class ModalDialogElement extends HTMLElement {
  static observedAttributes = ['open', 'title', 'confirm-label'];

  /** The element that had focus before the modal was opened – restored on close. */
  private previousFocus: Element | null = null;

  private readonly FOCUSABLE_SELECTOR =
    'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), ' +
    'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

  private readonly onKeydown = (e: KeyboardEvent): void => {
    if (!this.hasAttribute('open')) return;

    if (e.key === 'Escape') {
      this.cancel();
      return;
    }

    if (e.key === 'Tab') {
      const focusable = Array.from(
        this.shadowRoot!.querySelectorAll<HTMLElement>(this.FOCUSABLE_SELECTOR),
      ).filter((el) => el.offsetParent !== null || el.tagName === 'BUTTON');

      if (focusable.length === 0) {
        e.preventDefault();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = this.shadowRoot!.activeElement;

      if (e.shiftKey) {
        if (active === first) {
          e.preventDefault();
          last.focus();
        }
      } else {
        if (active === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
  };

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  connectedCallback(): void {
    document.addEventListener('keydown', this.onKeydown);
    this.render();
  }

  disconnectedCallback(): void {
    document.removeEventListener('keydown', this.onKeydown);
  }

  attributeChangedCallback(name: string): void {
    if (!this.isConnected) return;
    this.render();

    if (name === 'open') {
      if (this.hasAttribute('open')) {
        // Save current focus so we can restore it when the modal closes.
        this.previousFocus = document.activeElement;
        // Move focus into the modal after the render settles.
        requestAnimationFrame(() => {
          const first = this.shadowRoot!.querySelector<HTMLElement>(this.FOCUSABLE_SELECTOR);
          first?.focus();
        });
      } else {
        // Restore focus to the element that triggered the modal.
        if (this.previousFocus instanceof HTMLElement) {
          this.previousFocus.focus();
        }
        this.previousFocus = null;
      }
    }
  }

  private cancel(): void {
    this.removeAttribute('open');
    this.dispatchEvent(new CustomEvent('dialog-cancel', { bubbles: true, composed: true }));
  }

  private confirm(): void {
    this.dispatchEvent(new CustomEvent('dialog-confirm', { bubbles: true, composed: true }));
    // Parent is responsible for removing "open" when the operation succeeds.
  }

  private render(): void {
    const isOpen = this.hasAttribute('open');
    const title = this.getAttribute('title') ?? '';
    const confirmLabel = this.getAttribute('confirm-label') ?? 'Save';
    const isDanger = confirmLabel === 'Delete';

    this.shadowRoot!.innerHTML = `
      <style>${MODAL_DIALOG_STYLES}</style>
      <div class="overlay ${isOpen ? 'open' : ''}" id="overlay">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
          <div class="modal-header">
            <h3 id="modal-title">${title}</h3>
            <button class="close-btn" id="close-btn" aria-label="Close dialog">✕</button>
          </div>
          <div class="modal-body">
            <slot></slot>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" id="cancel-btn">Cancel</button>
            <button class="btn ${isDanger ? 'btn-danger' : 'btn-primary'}" id="confirm-btn">${confirmLabel}</button>
          </div>
        </div>
      </div>`;

    this.shadowRoot!.getElementById('close-btn')?.addEventListener('click', () => this.cancel());
    this.shadowRoot!.getElementById('cancel-btn')?.addEventListener('click', () => this.cancel());
    this.shadowRoot!.getElementById('confirm-btn')?.addEventListener('click', () => this.confirm());
    // Clicking the backdrop also cancels
    this.shadowRoot!.getElementById('overlay')?.addEventListener('click', (e) => {
      if (e.target === e.currentTarget) this.cancel();
    });
  }
}

if (!customElements.get('modal-dialog')) {
  customElements.define('modal-dialog', ModalDialogElement);
}

