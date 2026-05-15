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
 */
export class ModalDialogElement extends HTMLElement {
  static observedAttributes = ['open', 'title', 'confirm-label'];

  private readonly onKeydown = (e: KeyboardEvent): void => {
    if (e.key === 'Escape' && this.hasAttribute('open')) this.cancel();
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

  attributeChangedCallback(): void {
    if (this.isConnected) this.render();
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
      <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
        :host { font-family: system-ui, sans-serif; }
        .overlay {
          display: ${isOpen ? 'flex' : 'none'};
          position: fixed;
          inset: 0;
          background: rgba(0, 0, 0, 0.45);
          z-index: 1000;
          align-items: center;
          justify-content: center;
        }
        .modal {
          background: #fff;
          border-radius: 10px;
          box-shadow: 0 8px 40px rgba(0,0,0,0.2);
          width: 500px;
          max-width: 95vw;
          max-height: 90vh;
          display: flex;
          flex-direction: column;
        }
        .modal-header {
          padding: 16px 20px;
          border-bottom: 1px solid #e5e7eb;
          display: flex;
          align-items: center;
          justify-content: space-between;
        }
        .modal-header h3 { font-size: 16px; font-weight: 600; color: #111827; }
        .close-btn {
          background: none;
          border: none;
          cursor: pointer;
          font-size: 18px;
          color: #9ca3af;
          padding: 0 4px;
          line-height: 1;
        }
        .close-btn:hover { color: #374151; }
        .modal-body { padding: 20px; overflow-y: auto; flex: 1; }
        .modal-footer {
          padding: 12px 20px;
          border-top: 1px solid #e5e7eb;
          display: flex;
          justify-content: flex-end;
          gap: 8px;
        }
        .btn {
          padding: 8px 18px;
          border-radius: 6px;
          font-size: 14px;
          font-weight: 500;
          cursor: pointer;
          transition: background 0.12s;
        }
        .btn-secondary { background: #fff; color: #374151; border: 1px solid #d1d5db; }
        .btn-secondary:hover { background: #f9fafb; }
        .btn-primary { background: #4f46e5; color: #fff; border: none; }
        .btn-primary:hover { background: #4338ca; }
        .btn-danger  { background: #dc2626; color: #fff; border: none; }
        .btn-danger:hover  { background: #b91c1c; }
      </style>
      <div class="overlay" id="overlay">
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

