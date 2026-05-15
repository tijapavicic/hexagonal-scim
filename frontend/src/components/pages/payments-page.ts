import { createPayment, getPaymentById, listPayments } from '../../api/payments';
import type { CreatePaymentDto, PaymentDto } from '../../types/payment.dto';
import type { NotificationBarElement } from '../shared/notification-bar';
import '../shared/notification-bar';
import '../shared/pagination-bar';
import '../shared/modal-dialog';
import { PAYMENTS_PAGE_STYLES } from './payments-page.styles';

interface PaymentFormValues {
  amount: string;
  currency: string;
  status: string;
  userId: string;
}

type ModalState = { kind: 'none' } | { kind: 'create' };

class PaymentsPageElement extends HTMLElement {
  private payments: PaymentDto[] = [];
  private page = 0;
  private size = 10;
  private hasMore = false;
  private loading = false;
  private listError: string | null = null;

  private expandedPaymentId: number | null = null;
  private expandedPayment: PaymentDto | null = null;

  private modal: ModalState = { kind: 'none' };
  private formValues: PaymentFormValues = {
    amount: '',
    currency: 'EUR',
    status: 'PENDING',
    userId: '',
  };
  private formError: string | null = null;
  private submitting = false;

  private toastEl!: NotificationBarElement;

  connectedCallback(): void {
    this.innerHTML = '<notification-bar id="toast"></notification-bar><div id="main"></div>';
    this.toastEl = this.querySelector('#toast') as NotificationBarElement;
    this.render();
    void this.loadPayments();
  }

  private async loadPayments(): Promise<void> {
    this.loading = true;
    this.listError = null;
    this.render();

    try {
      const result = await listPayments(this.page, this.size);
      this.payments = result;
      this.hasMore = result.length === this.size;
    } catch (e) {
      this.payments = [];
      this.listError = e instanceof Error ? e.message : 'Failed to load payments.';
    } finally {
      this.loading = false;
      this.render();
    }
  }

  private readForm(): PaymentFormValues | null {
    const form = this.querySelector<HTMLFormElement>('#payment-form');
    if (!form) return null;
    return {
      amount: (form.querySelector<HTMLInputElement>('[name="amount"]')?.value ?? '').trim(),
      currency: (form.querySelector<HTMLSelectElement>('[name="currency"]')?.value ?? '').trim(),
      status: (form.querySelector<HTMLSelectElement>('[name="status"]')?.value ?? '').trim(),
      userId: (form.querySelector<HTMLInputElement>('[name="userId"]')?.value ?? '').trim(),
    };
  }

  private validateForm(values: PaymentFormValues): string | null {
    if (!values.amount) return 'Amount is required.';
    const numeric = Number(values.amount);
    if (!Number.isFinite(numeric) || numeric <= 0) return 'Amount must be greater than 0.';
    if (!values.currency) return 'Currency is required.';
    if (!values.status) return 'Status is required.';
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
      amount: values.amount,
      currency: values.currency,
      status: values.status,
      ...(values.userId ? { userId: Number(values.userId) } : {}),
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
    if (normalized === 'PENDING') return 'badge-pending';
    if (normalized === 'COMPLETED') return 'badge-completed';
    if (normalized === 'FAILED') return 'badge-failed';
    return 'badge-default';
  }

  private detailTemplate(payment: PaymentDto): string {
    const field = (label: string, value: string) =>
      `<div><div class="dl">${label}</div><div class="dv">${value}</div></div>`;

    return `
      <div class="detail-grid">
        ${field('ID', String(payment.id))}
        ${field('Amount', payment.amount)}
        ${field('Currency', payment.currency)}
        ${field('Status', payment.status)}
        ${field('User ID', payment.userId === null ? 'none' : String(payment.userId))}
        ${field('Created At', payment.createdAt)}
      </div>`;
  }

  private tableContent(): string {
    if (this.loading) return '<div class="loading-state" role="status" aria-label="Loading payments"><div class="spinner" aria-hidden="true"></div>Loading payments\u2026</div>';
    if (this.listError) {
      return `<div class="state-msg error-msg">${this.listError}<br><br>
        <button class="btn btn-secondary" data-action="reload">Retry</button>
      </div>`;
    }
    if (this.payments.length === 0) return '<div class="state-msg">No payments found.</div>';

    const rows = this.payments.flatMap((payment) => {
      const detailRow =
        this.expandedPaymentId === payment.id && this.expandedPayment
          ? `<tr class="detail-row"><td colspan="7">${this.detailTemplate(this.expandedPayment)}</td></tr>`
          : '';

      return `
        <tr class="payment-row">
          <td>${payment.id}</td>
          <td>${payment.amount}</td>
          <td>${payment.currency}</td>
          <td><span class="badge ${this.statusClass(payment.status)}">${payment.status}</span></td>
          <td>${payment.userId === null ? '-' : payment.userId}</td>
          <td>${payment.createdAt}</td>
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
            <th>Amount</th>
            <th>Currency</th>
            <th>Status</th>
            <th>User ID</th>
            <th>Created At</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>${rows.join('')}</tbody>
      </table>`;
  }

  private createModalTemplate(): string {
    const open = this.modal.kind === 'create';
    const confirmLabel = this.submitting ? 'Saving...' : 'Create';

    return `
      <modal-dialog id="payment-modal" title="Create Payment" confirm-label="${confirmLabel}" ${open ? 'open' : ''}>
        ${this.formError ? `<p class="form-error">${this.formError}</p>` : ''}
        <form id="payment-form" class="form-grid" autocomplete="off">
          <div class="field">
            <label for="p-amount">Amount</label>
            <input id="p-amount" type="text" name="amount" value="${this.formValues.amount}" placeholder="e.g. 19.99" required>
          </div>
          <div class="field">
            <label for="p-currency">Currency</label>
            <select id="p-currency" name="currency" required>
              ${['EUR', 'USD', 'GBP']
                .map((c) => `<option value="${c}" ${this.formValues.currency === c ? 'selected' : ''}>${c}</option>`)
                .join('')}
            </select>
          </div>
          <div class="field">
            <label for="p-status">Status</label>
            <select id="p-status" name="status" required>
              ${['PENDING', 'COMPLETED', 'FAILED']
                .map((s) => `<option value="${s}" ${this.formValues.status === s ? 'selected' : ''}>${s}</option>`)
                .join('')}
            </select>
          </div>
          <div class="field">
            <label for="p-userId">User ID (optional)</label>
            <input id="p-userId" type="text" name="userId" value="${this.formValues.userId}" placeholder="e.g. 42">
          </div>
        </form>
      </modal-dialog>`;
  }

  private mainTemplate(): string {
    return `
      <div class="page-header">
        <h2>Payments</h2>
        <button class="btn btn-primary" data-action="create">+ Create Payment</button>
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
      this.formValues = { amount: '', currency: 'EUR', status: 'PENDING', userId: '' };
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

