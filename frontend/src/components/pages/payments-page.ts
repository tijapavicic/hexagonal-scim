import { createPayment, getPaymentById, listPayments } from '../../api/payments';
import { listProducts } from '../../api/products';
import type { CreatePaymentDto, PaymentDto } from '../../types/payment.dto';
import type { ProductDto } from '../../types/product.dto';
import type { NotificationBarElement } from '../shared/notification-bar';
import '../shared/notification-bar';
import '../shared/pagination-bar';
import '../shared/modal-dialog';
import { PAYMENTS_PAGE_STYLES } from './payments-page.styles';
import { createLogger } from '../../logger/logger';

const logger = createLogger('PaymentsPage');

interface PaymentFormValues {
  productId: string;
  quantity: string;
  paymentMethod: string;
  currency: string;
  accountId: string;
}

type ModalState = { kind: 'none' } | { kind: 'create' };

class PaymentsPageElement extends HTMLElement {
  private payments: PaymentDto[] = [];
  private products: ProductDto[] = [];
  private page = 0;
  private size = 10;
  private hasMore = false;
  private loading = false;
  private listError: string | null = null;

  private expandedPaymentId: number | null = null;
  private expandedPayment: PaymentDto | null = null;

  private modal: ModalState = { kind: 'none' };
  private formValues: PaymentFormValues = {
    productId: '',
    quantity: '1',
    paymentMethod: 'BANK_ACCOUNT',
    currency: 'EUR',
    accountId: '',
  };
  private formError: string | null = null;
  private submitting = false;

  private toastEl!: NotificationBarElement;

  connectedCallback(): void {
    this.innerHTML = '<notification-bar id="toast"></notification-bar><div id="main"></div>';
    this.toastEl = this.querySelector('#toast') as NotificationBarElement;
    this.render();
    void this.loadPayments();
    void this.loadProducts();
  }

  private async loadProducts(): Promise<void> {
    try {
      logger.info('Loading products');
      this.products = await listProducts();
      // Pre-select the first product if none is selected
      if (this.products.length > 0 && !this.formValues.productId) {
        this.formValues.productId = String(this.products[0]!.id);
      }
      logger.info('Products loaded', { count: this.products.length });
    } catch (error) {
      // Products are a convenience — failure does not block the page
      logger.warn('Failed to load products', {
        error: error instanceof Error ? error.message : String(error),
      });
      this.products = [];
    }
  }

  private async loadPayments(): Promise<void> {
    this.loading = true;
    this.listError = null;
    this.render();

    try {
      logger.info('Loading payments', { page: this.page, size: this.size });
      const result = await listPayments(this.page, this.size);
      this.payments = result;
      this.hasMore = result.length === this.size;
      logger.info('Payments loaded successfully', {
        count: result.length,
        page: this.page,
        hasMore: this.hasMore,
      });
    } catch (e) {
      this.payments = [];
      this.listError = e instanceof Error ? e.message : 'Failed to load payments.';
      logger.error('Failed to load payments', {
        page: this.page,
        size: this.size,
        error: this.listError,
      });
    } finally {
      this.loading = false;
      this.render();
    }
  }

  private readForm(): PaymentFormValues | null {
    const form = this.querySelector<HTMLFormElement>('#payment-form');
    if (!form) return null;
    return {
      productId:     (form.querySelector<HTMLSelectElement>('[name="productId"]')?.value ?? '').trim(),
      quantity:      (form.querySelector<HTMLInputElement>('[name="quantity"]')?.value ?? '1').trim(),
      paymentMethod: (form.querySelector<HTMLSelectElement>('[name="paymentMethod"]')?.value ?? '').trim(),
      currency:      (form.querySelector<HTMLSelectElement>('[name="currency"]')?.value ?? '').trim(),
      accountId:     (form.querySelector<HTMLInputElement>('[name="accountId"]')?.value ?? '').trim(),
    };
  }

  private validateForm(values: PaymentFormValues): string | null {
    if (!values.productId || isNaN(Number(values.productId)) || Number(values.productId) <= 0)
      return 'Please select a product.';
    const qty = Number(values.quantity);
    if (!Number.isInteger(qty) || qty < 1) return 'Quantity must be a whole number ≥ 1.';
    if (!values.paymentMethod) return 'Payment method is required.';
    if (!values.currency)      return 'Currency is required.';
    return null;
  }

  private async handleCreateSubmit(): Promise<void> {
    const values = this.readForm();
    if (!values) return;

    const validationError = this.validateForm(values);
    if (validationError) {
      this.formError = validationError;
      this.render();
      return;
    }

    this.formValues = values;
    this.formError = null;
    this.submitting = true;
    this.render();

    const body: CreatePaymentDto = {
      productId:     Number(values.productId),
      quantity:      Number(values.quantity),
      paymentMethod: values.paymentMethod,
      currency:      values.currency,
      ...(values.accountId ? { accountId: Number(values.accountId) } : {}),
    };

    try {
      await createPayment(body);
      this.toastEl.show('Payment created.', 'success');
      this.modal = { kind: 'none' };
      this.formError = null;
      this.expandedPaymentId = null;
      this.expandedPayment = null;
      await this.loadPayments();
    } catch (e) {
      this.formError = e instanceof Error ? e.message : 'Create payment failed.';
    } finally {
      this.submitting = false;
      this.render();
    }
  }

  private statusClass(status: string): string {
    const normalized = status.toUpperCase();
    if (normalized === 'PENDING')   return 'badge-pending';
    if (normalized === 'COMPLETED') return 'badge-completed';
    if (normalized === 'FAILED')    return 'badge-failed';
    return 'badge-default';
  }

  private detailTemplate(payment: PaymentDto): string {
    const field = (label: string, value: string) =>
      `<div><div class="dl">${label}</div><div class="dv">${value}</div></div>`;
    const productName = this.products.find((p) => p.id === payment.productId)?.name ?? '–';

    return `
      <div class="detail-grid">
        ${field('ID',             String(payment.id))}
        ${field('Product',        payment.productId === null ? '–' : `${productName} (#${payment.productId})`)}
        ${field('Quantity',       String(payment.quantity))}
        ${field('Total Amount',   payment.totalAmount)}
        ${field('Currency',       payment.currency)}
        ${field('Status',         payment.status)}
        ${field('Payment Method', payment.paymentMethod)}
        ${field('User ID',        payment.userId    === null ? '–' : String(payment.userId))}
        ${field('Account ID',     payment.accountId === null ? '–' : String(payment.accountId))}
      </div>`;
  }

  private tableContent(): string {
    if (this.loading)
      return '<div class="loading-state" role="status" aria-label="Loading payments"><div class="spinner" aria-hidden="true"></div>Loading payments\u2026</div>';
    if (this.listError) {
      return `<div class="state-msg error-msg">${this.listError}<br><br>
        <button class="btn btn-secondary" data-action="reload">Retry</button>
      </div>`;
    }
    if (this.payments.length === 0) return '<div class="state-msg">No payments found.</div>';

    const rows = this.payments.flatMap((payment) => {
      const productName = this.products.find((p) => p.id === payment.productId)?.name ?? payment.productId ?? '–';
      const detailRow =
        this.expandedPaymentId === payment.id && this.expandedPayment
          ? `<tr class="detail-row"><td colspan="7">${this.detailTemplate(this.expandedPayment)}</td></tr>`
          : '';

      return `
        <tr class="payment-row">
          <td>${payment.id}</td>
          <td>${productName}</td>
          <td>${payment.quantity}</td>
          <td>${payment.totalAmount}</td>
          <td>${payment.currency}</td>
          <td><span class="badge ${this.statusClass(payment.status)}">${payment.status}</span></td>
          <td>
            <div class="actions">
              <button class="btn btn-secondary" data-action="toggle-detail" data-id="${payment.id}">View</button>
            </div>
          </td>
        </tr>
        ${detailRow}`;
    });

    return `
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>Product</th>
            <th>Qty</th>
            <th>Total</th>
            <th>Currency</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>${rows.join('')}</tbody>
      </table>`;
  }

  private productOptions(): string {
    if (this.products.length === 0) {
      return `<option value="">Loading products…</option>`;
    }
    return this.products
      .map((p) => `<option value="${p.id}" ${this.formValues.productId === String(p.id) ? 'selected' : ''}>
          ${p.name} — ${p.price} ${p.currency} (stock: ${p.stockQuantity})
        </option>`)
      .join('');
  }

  private createModalTemplate(): string {
    const open = this.modal.kind === 'create';
    const confirmLabel = this.submitting ? 'Saving...' : 'Purchase';

    return `
      <modal-dialog id="payment-modal" title="New Payment / Purchase" confirm-label="${confirmLabel}" ${open ? 'open' : ''}>
        ${this.formError ? `<p class="form-error">${this.formError}</p>` : ''}
        <form id="payment-form" class="form-grid" autocomplete="off">

          <div class="field">
            <label for="p-productId">Product <span class="req">*</span></label>
            <select id="p-productId" name="productId" required>
              ${this.productOptions()}
            </select>
          </div>

          <div class="field">
            <label for="p-quantity">Quantity <span class="req">*</span></label>
            <input id="p-quantity" type="number" name="quantity" min="1"
                   value="${this.formValues.quantity}" placeholder="e.g. 1" required>
          </div>

          <div class="field">
            <label for="p-paymentMethod">Payment Method <span class="req">*</span></label>
            <select id="p-paymentMethod" name="paymentMethod" required>
              ${['BANK_ACCOUNT', 'PAYPAL', 'IDEAL']
                .map((m) => `<option value="${m}" ${this.formValues.paymentMethod === m ? 'selected' : ''}>${m.replace('_', ' ')}</option>`)
                .join('')}
            </select>
          </div>

          <div class="field">
            <label for="p-currency">Currency <span class="req">*</span></label>
            <select id="p-currency" name="currency" required>
              ${['EUR', 'USD']
                .map((c) => `<option value="${c}" ${this.formValues.currency === c ? 'selected' : ''}>${c}</option>`)
                .join('')}
            </select>
          </div>

          <p class="form-hint">
            Optional: provide your Account ID to debit it on success.
            Your user is identified automatically from your session.
          </p>

          <div class="field">
            <label for="p-accountId">Your Account ID</label>
            <input id="p-accountId" type="number" name="accountId" min="1"
                   value="${this.formValues.accountId}" placeholder="Leave blank to skip debit">
          </div>

        </form>
      </modal-dialog>`;
  }

  private mainTemplate(): string {
    return `
      <div class="page-header">
        <h2>Payments</h2>
        <button class="btn btn-primary" data-action="create">+ New Payment</button>
      </div>

      <div class="card">${this.tableContent()}</div>

      ${!this.loading && !this.listError
        ? `<pagination-bar page="${this.page}" size="${this.size}" has-more="${this.hasMore}"></pagination-bar>`
        : ''}

      ${this.createModalTemplate()}
    `;
  }

  private render(): void {
    const main = this.querySelector<HTMLElement>('#main');
    if (!main) return;
    main.innerHTML = PAYMENTS_PAGE_STYLES + this.mainTemplate();
    this.bindEvents();
  }

  private bindEvents(): void {
    this.querySelector('[data-action="create"]')?.addEventListener('click', () => {
      const firstProductId = this.products.length > 0 ? String(this.products[0]!.id) : '';
      this.formValues = { productId: firstProductId, quantity: '1', paymentMethod: 'BANK_ACCOUNT', currency: 'EUR', accountId: '' };
      this.formError = null;
      this.modal = { kind: 'create' };
      this.render();
    });

    this.querySelector('[data-action="reload"]')?.addEventListener('click', () => {
      void this.loadPayments();
    });

    this.querySelectorAll<HTMLElement>('[data-action="toggle-detail"]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const id = Number(btn.dataset['id'] ?? '0');

        if (this.expandedPaymentId === id) {
          this.expandedPaymentId = null;
          this.expandedPayment = null;
          this.render();
          return;
        }

        try {
          this.expandedPayment = await getPaymentById(id);
          this.expandedPaymentId = id;
          this.render();
        } catch {
          this.toastEl.show('Failed to load payment details.', 'error');
        }
      });
    });

    this.querySelector('#payment-modal')?.addEventListener('dialog-confirm', () => {
      void this.handleCreateSubmit();
    });
    this.querySelector('#payment-modal')?.addEventListener('dialog-cancel', () => {
      this.modal = { kind: 'none' };
      this.formError = null;
    });

    this.querySelector('pagination-bar')?.addEventListener('page-change', (e) => {
      const { page, size } = (e as CustomEvent<{ page: number; size: number }>).detail;
      this.page = page;
      this.size = size;
      this.expandedPaymentId = null;
      this.expandedPayment = null;
      void this.loadPayments();
    });

    this.querySelector('pagination-bar')?.addEventListener('size-change', (e) => {
      const { size } = (e as CustomEvent<{ page: number; size: number }>).detail;
      this.page = 0;
      this.size = size;
      this.expandedPaymentId = null;
      this.expandedPayment = null;
      void this.loadPayments();
    });
  }
}

if (!customElements.get('payments-page')) {
  customElements.define('payments-page', PaymentsPageElement);
}

