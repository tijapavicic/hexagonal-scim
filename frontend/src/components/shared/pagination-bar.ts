/**
 * <pagination-bar> — prev/next with page-size selector.
 *
 * Attributes:
 *   page        – 0-based current page   (default: 0)
 *   size        – items per page          (default: 10)
 *   has-more    – "true" if next page may exist (default: "false")
 *
 * Events (bubbles + composed):
 *   page-change  – detail: { page: number, size: number }
 *   size-change  – detail: { page: 0,     size: number }
 */
export class PaginationBarElement extends HTMLElement {
  static observedAttributes = ['page', 'size', 'has-more'];

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

  get page(): number {
    return parseInt(this.getAttribute('page') ?? '0', 10);
  }

  get size(): number {
    return parseInt(this.getAttribute('size') ?? '10', 10);
  }

  get hasMore(): boolean {
    return this.getAttribute('has-more') === 'true';
  }

  private render(): void {
    const { page, size, hasMore } = this;

    this.shadowRoot!.innerHTML = `
      <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
        :host {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 12px 0;
          font-family: system-ui, sans-serif;
          font-size: 13px;
        }
        button {
          padding: 6px 14px;
          border: 1px solid #d1d5db;
          border-radius: 6px;
          background: #fff;
          cursor: pointer;
          font-size: 13px;
          transition: background 0.12s;
        }
        button:not(:disabled):hover { background: #f3f4f6; }
        button:disabled { opacity: 0.45; cursor: default; }
        .info { color: #4b5563; }
        select {
          padding: 5px 8px;
          border: 1px solid #d1d5db;
          border-radius: 6px;
          font-size: 13px;
          background: #fff;
          cursor: pointer;
        }
      </style>
      <button id="prev" ${page === 0 ? 'disabled' : ''}>← Prev</button>
      <span class="info">Page ${page + 1}</span>
      <button id="next" ${!hasMore ? 'disabled' : ''}>Next →</button>
      <select id="size-sel" aria-label="Page size">
        ${[10, 25, 50]
          .map((s) => `<option value="${s}" ${s === size ? 'selected' : ''}>${s} / page</option>`)
          .join('')}
      </select>`;

    this.shadowRoot!.getElementById('prev')?.addEventListener('click', () => {
      if (page > 0) {
        this.dispatchEvent(
          new CustomEvent('page-change', {
            detail: { page: page - 1, size },
            bubbles: true,
            composed: true,
          }),
        );
      }
    });

    this.shadowRoot!.getElementById('next')?.addEventListener('click', () => {
      if (hasMore) {
        this.dispatchEvent(
          new CustomEvent('page-change', {
            detail: { page: page + 1, size },
            bubbles: true,
            composed: true,
          }),
        );
      }
    });

    this.shadowRoot!.getElementById('size-sel')?.addEventListener('change', (e) => {
      const newSize = parseInt((e.target as HTMLSelectElement).value, 10);
      this.dispatchEvent(
        new CustomEvent('size-change', {
          detail: { page: 0, size: newSize },
          bubbles: true,
          composed: true,
        }),
      );
    });
  }
}

if (!customElements.get('pagination-bar')) {
  customElements.define('pagination-bar', PaginationBarElement);
}

