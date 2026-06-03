import { listProducts, getProductById, createProduct, updateProduct, deleteProduct } from '../../api/products';
import { getAuthProfile } from '../../auth/keycloak';
import type { ProductDto } from '../../types/product.dto';
import type { NotificationBarElement } from '../shared/notification-bar';
import '../shared/notification-bar';
import '../shared/pagination-bar';
import '../shared/modal-dialog';
import { PRODUCTS_PAGE_STYLES } from './products-page.styles';

interface ProductFormValues {
  name: string;
  description: string;
  price: string;
  currency: string;
  stockQuantity: string;
}

type ModalState =
  | { kind: 'none' }
  | { kind: 'create' }
  | { kind: 'edit'; product: ProductDto }
  | { kind: 'delete'; product: ProductDto };

class ProductsPageElement extends HTMLElement {
  private products: ProductDto[] = [];
  private loading = false;
  private listError: string | null = null;
  private expandedProductId: number | null = null;
  private expandedProduct: ProductDto | null = null;
  private modal: ModalState = { kind: 'none' };
  private formValues: ProductFormValues = {
    name: '',
    description: '',
    price: '',
    currency: 'EUR',
    stockQuantity: '',
  };
  private formError: string | null = null;
  private submitting = false;
  private isAdmin = false;
  private toastEl!: NotificationBarElement;

  private hasAdminRole(realmRoles: string[]): boolean {
    return realmRoles.some((role) => {
      const normalized = role.toUpperCase();
      return normalized === 'ADMIN' || normalized === 'ROLE_ADMIN';
    });
  }

  connectedCallback(): void {
    this.isAdmin = this.hasAdminRole(getAuthProfile().realmRoles);
    this.innerHTML = '<notification-bar id="toast"></notification-bar><div id="main"></div>';
    this.toastEl = this.querySelector('#toast') as NotificationBarElement;
    this.render();
    void this.loadProducts();
  }

  private async loadProducts(): Promise<void> {
    this.loading = true;
    this.listError = null;
    this.render();
    try {
      this.products = await listProducts();
    } catch (e) {
      this.products = [];
      this.listError = e instanceof Error ? e.message : 'Failed to load products.';
    } finally {
      this.loading = false;
      this.render();
    }
  }

  private readForm(): ProductFormValues | null {
    const f = this.querySelector<HTMLFormElement>('#product-form');
    if (!f) return null;
    return {
      name: (f.querySelector<HTMLInputElement>('[name="name"]')?.value ?? '').trim(),
      description: (f.querySelector<HTMLTextAreaElement>('[name="description"]')?.value ?? '').trim(),
      price: (f.querySelector<HTMLInputElement>('[name="price"]')?.value ?? '').trim(),
      currency: (f.querySelector<HTMLSelectElement>('[name="currency"]')?.value ?? 'EUR').trim(),
      stockQuantity: (f.querySelector<HTMLInputElement>('[name="stockQuantity"]')?.value ?? '').trim(),
    };
  }

  private validate(v: ProductFormValues): string | null {
    if (!v.name) return 'Name is required.';
    if (!v.price || isNaN(Number(v.price)) || Number(v.price) <= 0) return 'Price must be > 0.';
    if (!v.stockQuantity || isNaN(Number(v.stockQuantity)) || Number(v.stockQuantity) < 0)
      return 'Stock quantity must be ≥ 0.';
    return null;
  }

  private async handleFormSubmit(): Promise<void> {
    const v = this.readForm();
    if (!v) return;
    const err = this.validate(v);
    if (err) {
      this.formError = err;
      this.render();
      return;
    }
    this.formValues = v;
    this.submitting = true;
    this.formError = null;
    this.render();

    const body: Partial<ProductDto> = {
      name: v.name,
      description: v.description,
      price: v.price,
      currency: v.currency,
      stockQuantity: Number(v.stockQuantity),
    };

    try {
      if (this.modal.kind === 'create') {
        await createProduct(body);
        this.toastEl.show('Product created.', 'success');
      } else if (this.modal.kind === 'edit') {
        await updateProduct(this.modal.product.id, body);
        this.toastEl.show('Product updated.', 'success');
      }
      this.modal = { kind: 'none' };
      this.formError = null;
      this.expandedProductId = null;
      this.expandedProduct = null;
      await this.loadProducts();
    } catch (e) {
      this.formError = e instanceof Error ? e.message : 'Operation failed.';
    } finally {
      this.submitting = false;
      this.render();
    }
  }

  private async handleDeleteConfirm(): Promise<void> {
    if (this.modal.kind !== 'delete') return;
    const { product } = this.modal;
    try {
      await deleteProduct(product.id);
      this.modal = { kind: 'none' };
      this.expandedProductId = null;
      this.expandedProduct = null;
      this.toastEl.show(`Product "${product.name}" deleted.`, 'success');
      await this.loadProducts();
    } catch (e) {
      this.modal = { kind: 'none' };
      this.toastEl.show(e instanceof Error ? e.message : 'Delete failed.', 'error');
      this.render();
    }
  }

  private render(): void {
    const main = this.querySelector<HTMLElement>('#main');
    if (!main) return;
    main.innerHTML = PRODUCTS_PAGE_STYLES + this.mainTpl();
    this.bindEvents();
  }

  private mainTpl(): string {
    return `
      <div class="page-header">
        <h2>Products</h2>
        ${this.isAdmin ? `<button class="btn btn-primary" data-action="create">+ Create Product</button>` : ''}
      </div>
      <div class="card">${this.listContent()}</div>
      ${this.productModalTpl()}
      ${this.deleteModalTpl()}`;
  }

  private listContent(): string {
    if (this.loading)
      return '<div class="loading-state" role="status" aria-label="Loading products"><div class="spinner" aria-hidden="true"></div>Loading products\u2026</div>';
    if (this.listError)
      return `<div class="state-msg error-msg">${this.listError}<br><br>
        <button class="btn btn-secondary" data-action="reload">\u21ba Retry</button></div>`;
    if (!this.products.length) return '<div class="state-msg">No products found.</div>';

    const rows = this.products.flatMap((p) => {
      const detail =
        this.expandedProductId === p.id && this.expandedProduct
          ? `<tr class="detail-row"><td colspan="6">${this.detailTpl(this.expandedProduct)}</td></tr>`
          : '';
      return `<tr class="product-row">
        <td>${p.id}</td>
        <td><button class="btn-ghost" data-action="toggle" data-id="${p.id}">${p.name}</button></td>
        <td>${p.price} ${p.currency}</td>
        <td>${p.stockQuantity}</td>
        <td>${p.description || '–'}</td>
        <td><div class="actions">${this.isAdmin ? `
          <button class="btn btn-secondary" data-action="edit"   data-id="${p.id}">Edit</button>
          <button class="btn btn-danger"    data-action="delete" data-id="${p.id}">Delete</button>` : ''}</div></td>
      </tr>${detail}`;
    });
    return `<table><thead><tr>
      <th>ID</th><th>Name</th><th>Price</th><th>Stock</th><th>Description</th><th>Actions</th>
      </tr></thead><tbody>${rows.join('')}</tbody></table>`;
  }

  private detailTpl(p: ProductDto): string {
    const f = (label: string, val: string | number) =>
      `<div><div class="dl">${label}</div><div class="dv">${val}</div></div>`;
    return `<div class="detail-grid">
      ${f('ID', p.id)}${f('Name', p.name)}${f('Price', `${p.price} ${p.currency}`)}
      ${f('Stock', p.stockQuantity)}${f('Description', p.description || '-')}</div>`;
  }

  private productModalTpl(): string {
    const show = this.modal.kind === 'create' || this.modal.kind === 'edit';
    const title = this.modal.kind === 'edit' ? 'Edit Product' : 'Create Product';
    const lbl = this.submitting ? 'Saving\u2026' : 'Save';
    const v = this.formValues;
    return `<modal-dialog id="product-modal" title="${title}" confirm-label="${lbl}" ${show ? 'open' : ''}>
      ${this.formError ? `<p class="form-error">\u26a0 ${this.formError}</p>` : ''}
      <form id="product-form" class="form-grid" autocomplete="off">
        <div class="field"><label for="pn">Name <span class="req">*</span></label>
          <input id="pn" type="text" name="name" value="${v.name}" required></div>
        <div class="field"><label for="pp">Price <span class="req">*</span></label>
          <input id="pp" type="number" step="0.01" name="price" value="${v.price}" required></div>
        <div class="field"><label for="pc">Currency</label>
          <select id="pc" name="currency">
            <option value="EUR" ${v.currency === 'EUR' ? 'selected' : ''}>EUR</option>
            <option value="USD" ${v.currency === 'USD' ? 'selected' : ''}>USD</option>
          </select></div>
        <div class="field"><label for="ps">Stock <span class="req">*</span></label>
          <input id="ps" type="number" name="stockQuantity" value="${v.stockQuantity}" required></div>
        <div class="field field-full"><label for="pd">Description</label>
          <textarea id="pd" name="description" rows="3">${v.description}</textarea></div>
      </form></modal-dialog>`;
  }

  private deleteModalTpl(): string {
    if (this.modal.kind !== 'delete')
      return `<modal-dialog id="delete-modal" title="Confirm Delete" confirm-label="Delete"></modal-dialog>`;
    const { product } = this.modal;
    return `<modal-dialog id="delete-modal" title="Confirm Delete" confirm-label="Delete" open>
      <p class="modal-help">
        Delete product <strong>${product.name}</strong>? This action cannot be undone.</p>
      </modal-dialog>`;
  }

  private bindEvents(): void {
    this.querySelector('[data-action="create"]')?.addEventListener('click', () => {
      this.formValues = { name: '', description: '', price: '', currency: 'EUR', stockQuantity: '' };
      this.formError = null;
      this.modal = { kind: 'create' };
      this.render();
    });

    this.querySelector('[data-action="reload"]')?.addEventListener('click', () => void this.loadProducts());

    this.querySelectorAll<HTMLElement>('[data-action="edit"]').forEach((btn) =>
      btn.addEventListener('click', async () => {
        const id = parseInt(btn.dataset['id'] ?? '0', 10);
        try {
          const product = await getProductById(id);
          this.formValues = {
            name: product.name,
            description: product.description || '',
            price: product.price,
            currency: product.currency,
            stockQuantity: String(product.stockQuantity),
          };
          this.formError = null;
          this.modal = { kind: 'edit', product };
          this.render();
        } catch {
          this.toastEl.show('Failed to load product.', 'error');
        }
      })
    );

    this.querySelectorAll<HTMLElement>('[data-action="delete"]').forEach((btn) =>
      btn.addEventListener('click', () => {
        const id = parseInt(btn.dataset['id'] ?? '0', 10);
        const product = this.products.find((p) => p.id === id);
        if (product) {
          this.modal = { kind: 'delete', product };
          this.render();
        }
      })
    );

    this.querySelectorAll<HTMLElement>('[data-action="toggle"]').forEach((btn) =>
      btn.addEventListener('click', async () => {
        const id = parseInt(btn.dataset['id'] ?? '0', 10);
        if (this.expandedProductId === id) {
          this.expandedProductId = null;
          this.expandedProduct = null;
          this.render();
        } else {
          try {
            this.expandedProduct = await getProductById(id);
            this.expandedProductId = id;
            this.render();
          } catch {
            this.toastEl.show('Failed to load product details.', 'error');
          }
        }
      })
    );

    this.querySelector('#product-modal')?.addEventListener('dialog-confirm', () => void this.handleFormSubmit());
    this.querySelector('#product-modal')?.addEventListener('dialog-cancel', () => {
      this.modal = { kind: 'none' };
      this.formError = null;
    });
    this.querySelector('#delete-modal')?.addEventListener('dialog-confirm', () => void this.handleDeleteConfirm());
    this.querySelector('#delete-modal')?.addEventListener('dialog-cancel', () => {
      this.modal = { kind: 'none' };
    });
  }
}

if (!customElements.get('products-page')) {
  customElements.define('products-page', ProductsPageElement);
}

