import { listUsers, getUserById, createUser, updateUser, deleteUser } from '../../api/users';
import { listAccounts, createAccount, topUpAccount, deleteAccount } from '../../api/accounts';
import { getAuthProfile } from '../../auth/keycloak';
import type { UserDto, CreateUserDto } from '../../types/user.dto';
import { UserRole } from '../../types/user.dto';
import type { AccountDto } from '../../types/account.dto';
import type { NotificationBarElement } from '../shared/notification-bar';
import '../shared/notification-bar';
import '../shared/pagination-bar';
import '../shared/modal-dialog';
import { USERS_PAGE_STYLES } from './users-page.styles';

interface FormValues {
  email: string;
  displayName: string;
  role: UserRole;
}


type ModalState =
  | { kind: 'none' }
  | { kind: 'create' }
  | { kind: 'edit'; user: UserDto }
  | { kind: 'delete'; user: UserDto }
  | { kind: 'create-account'; userId: number }
  | { kind: 'topup-account'; userId: number; account: AccountDto }
  | { kind: 'delete-account'; userId: number; account: AccountDto };

class UsersPageElement extends HTMLElement {
  private users: UserDto[] = [];
  private page = 0; private size = 10; private hasMore = false;
  private loading = false; private listError: string | null = null;
  private expandedUserId: number | null = null; private expandedUser: UserDto | null = null;
  private accounts: AccountDto[] = [];
  private accountsLoading = false;
  private modal: ModalState = { kind: 'none' };
  private formValues: FormValues = { email: '', displayName: '', role: UserRole.BUYER };
  private formError: string | null = null; private submitting = false;
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
    void this.loadUsers();
  }

  private async loadUsers(): Promise<void> {
    this.loading = true; this.listError = null; this.render();
    try {
      const r = await listUsers(this.page, this.size);
      this.users = r; this.hasMore = r.length === this.size;
    } catch (e) {
      this.users = []; this.listError = e instanceof Error ? e.message : 'Failed to load users.';
    } finally { this.loading = false; this.render(); }
  }

  private readForm(): FormValues | null {
    const f = this.querySelector<HTMLFormElement>('#user-form');
    if (!f) return null;
    return {
      email: (f.querySelector<HTMLInputElement>('[name="email"]')?.value ?? '').trim(),
      displayName: (f.querySelector<HTMLInputElement>('[name="displayName"]')?.value ?? '').trim(),
      role: (f.querySelector<HTMLSelectElement>('[name="role"]')?.value as UserRole) ?? UserRole.BUYER,
    };
  }

  private validate(v: FormValues): string | null {
    if (!v.email) return 'Email is required.';
    if (!v.displayName) return 'Display name is required.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email)) return 'Enter a valid email address.';
    return null;
  }

  private async handleFormSubmit(): Promise<void> {
    const v = this.readForm(); if (!v) return;
    const err = this.validate(v);
    if (err) { this.formError = err; this.render(); return; }
    this.formValues = v; this.submitting = true; this.formError = null; this.render();
    const body: CreateUserDto = { email: v.email, displayName: v.displayName, role: v.role };
    try {
      if (this.modal.kind === 'create') { await createUser(body); this.toastEl.show('User created.', 'success'); }
      else if (this.modal.kind === 'edit') { await updateUser(this.modal.user.id, body); this.toastEl.show('User updated.', 'success'); }
      this.modal = { kind: 'none' }; this.formError = null;
      this.expandedUserId = null; this.expandedUser = null;
      await this.loadUsers();
    } catch (e) { this.formError = e instanceof Error ? e.message : 'Operation failed.'; }
    finally { this.submitting = false; this.render(); }
  }

  private async handleDeleteConfirm(): Promise<void> {
    if (this.modal.kind !== 'delete') return;
    const { user } = this.modal;
    try {
      await deleteUser(user.id);
      this.modal = { kind: 'none' }; this.expandedUserId = null; this.expandedUser = null;
      this.toastEl.show(`User "${user.displayName}" deleted.`, 'success');
      await this.loadUsers();
    } catch (e) {
      this.modal = { kind: 'none' };
      this.toastEl.show(e instanceof Error ? e.message : 'Delete failed.', 'error');
      this.render();
    }
  }

  private async loadAccounts(userId: number): Promise<void> {
    this.accountsLoading = true;
    this.accounts = [];
    this.render();
    try {
      this.accounts = await listAccounts(userId);
    } catch {
      this.accounts = [];
    } finally {
      this.accountsLoading = false;
      this.render();
    }
  }

  private async handleCreateAccount(): Promise<void> {
    if (this.modal.kind !== 'create-account') return;
    const userId = this.modal.userId;
    const name = (this.querySelector<HTMLInputElement>('[name="accountName"]')?.value ?? '').trim();
    if (!name) {
      this.formError = 'Account name is required.';
      this.render();
      return;
    }
    this.submitting = true;
    this.formError = null;
    this.render();
    try {
      await createAccount(userId, { name });
      this.toastEl.show('Account created.', 'success');
      this.modal = { kind: 'none' };
      await this.loadAccounts(userId);
    } catch (e) {
      this.formError = e instanceof Error ? e.message : 'Create account failed.';
    } finally {
      this.submitting = false;
      this.render();
    }
  }

  private async handleTopUpAccount(): Promise<void> {
    if (this.modal.kind !== 'topup-account') return;
    const userId = this.modal.userId;
    const accountId = this.modal.account.id;
    const amountStr = (this.querySelector<HTMLInputElement>('[name="topupAmount"]')?.value ?? '').trim();
    const amount = Number(amountStr);
    if (!amountStr || isNaN(amount) || amount <= 0) {
      this.formError = 'Amount must be > 0.';
      this.render();
      return;
    }
    this.submitting = true;
    this.formError = null;
    this.render();
    try {
      await topUpAccount(userId, accountId, { amount });
      this.toastEl.show('Account topped up.', 'success');
      this.modal = { kind: 'none' };
      await this.loadAccounts(userId);
    } catch (e) {
      this.formError = e instanceof Error ? e.message : 'Top-up failed.';
    } finally {
      this.submitting = false;
      this.render();
    }
  }

  private async handleDeleteAccount(): Promise<void> {
    if (this.modal.kind !== 'delete-account') return;
    const { userId, account } = this.modal;
    try {
      await deleteAccount(userId, account.id);
      this.modal = { kind: 'none' };
      this.toastEl.show(`Account "${account.name}" deleted.`, 'success');
      await this.loadAccounts(userId);
    } catch (e) {
      this.modal = { kind: 'none' };
      this.toastEl.show(e instanceof Error ? e.message : 'Delete failed.', 'error');
      this.render();
    }
  }

  private render(): void {
    const main = this.querySelector<HTMLElement>('#main'); if (!main) return;
    main.innerHTML = USERS_PAGE_STYLES + this.mainTpl();
    this.bindEvents();
  }

  private mainTpl(): string {
    const { page, size, hasMore, loading, listError } = this;
    return `
      <div class="page-header">
        <h2>Users</h2>
        ${this.isAdmin ? `<button class="btn btn-primary" data-action="create">+ Create User</button>` : ''}
      </div>
      <div class="card">${this.listContent()}</div>
      ${!loading && !listError
        ? `<pagination-bar page="${page}" size="${size}" has-more="${hasMore}"></pagination-bar>`
        : ''}
      ${this.userModalTpl()}
      ${this.deleteModalTpl()}
      ${this.accountModalsTpl()}`;
  }

  private listContent(): string {
    if (this.loading) return '<div class="loading-state" role="status" aria-label="Loading users"><div class="spinner" aria-hidden="true"></div>Loading users\u2026</div>';
    if (this.listError) return `<div class="state-msg error-msg">${this.listError}<br><br>
      <button class="btn btn-secondary" data-action="reload">\u21ba Retry</button></div>`;
    if (!this.users.length) return '<div class="state-msg">No users found.</div>';
    const rows = this.users.flatMap(u => {
      const detail = this.expandedUserId === u.id && this.expandedUser
        ? `<tr class="detail-row"><td colspan="6">${this.detailTpl(this.expandedUser)}</td></tr>` : '';
      const roleBadge = this.getRoleBadge(u.role);
      return `<tr class="user-row">
        <td>${u.id}</td>
        <td><button class="btn-ghost" data-action="toggle" data-id="${u.id}">${u.displayName}</button></td>
        <td>${u.email}</td>
        <td>${roleBadge}</td>
        <td>${u.keycloakId ?? '-'}</td>
        <td><div class="actions">${this.isAdmin ? `
          <button class="btn btn-secondary" data-action="edit"   data-id="${u.id}">Edit</button>
          <button class="btn btn-danger"    data-action="delete" data-id="${u.id}">Delete</button>` : ''}</div></td>
      </tr>${detail}`;
    });
    return `<table><thead><tr>
      <th>ID</th><th>Display Name</th><th>Email</th><th>Role</th><th>Keycloak ID</th><th>Actions</th>
      </tr></thead><tbody>${rows.join('')}</tbody></table>`;
  }

  private getRoleBadge(role: UserRole): string {
    const className = role === UserRole.ADMIN ? 'badge-admin' : role === UserRole.SELLER ? 'badge-seller' : 'badge-buyer';
    return `<span class="badge ${className}">${role}</span>`;
  }

  private detailTpl(u: UserDto): string {
    const f = (label: string, val: string) => `<div><div class="dl">${label}</div><div class="dv">${val}</div></div>`;
    const roleBadge = this.getRoleBadge(u.role);
    return `
      <div class="detail-grid">
        ${f('ID', String(u.id))}${f('Display Name', u.displayName)}${f('Email', u.email)}
        <div><div class="dl">Role</div><div class="dv">${roleBadge}</div></div>
        ${f('Keycloak ID', u.keycloakId ?? '-')}
      </div>
      <div class="accounts-section">
        <div class="section-header">
          <h4>Accounts</h4>
          <button class="btn btn-sm btn-primary" data-action="create-account" data-user-id="${u.id}">+ Add Account</button>
        </div>
        ${this.accountsContent(u.id)}
      </div>`;
  }

  private accountsContent(userId: number): string {
    if (this.accountsLoading) {
      return '<div class="loading-state-sm">Loading accounts…</div>';
    }
    if (!this.accounts.length) {
      return '<div class="state-msg-sm">No accounts found.</div>';
    }
    const rows = this.accounts.map((a) => `
      <tr>
        <td>${a.id}</td>
        <td>${a.name}</td>
        <td>${a.balance}</td>
        <td class="actions-cell">
          ${this.isAdmin ? `<button class="btn btn-xs btn-secondary" data-action="topup-account" data-user-id="${userId}" data-account-id="${a.id}">Top Up</button>` : ''}
          <button class="btn btn-xs btn-danger" data-action="delete-account" data-user-id="${userId}" data-account-id="${a.id}">Delete</button>
        </td>
      </tr>`).join('');
    return `<table class="accounts-table">
      <thead><tr><th>ID</th><th>Name</th><th>Balance</th><th>Actions</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>`;
  }

  private userModalTpl(): string {
    const show = this.modal.kind === 'create' || this.modal.kind === 'edit';
    const title = this.modal.kind === 'edit' ? 'Edit User' : 'Create User';
    const lbl = this.submitting ? 'Saving\u2026' : 'Save';
    const v = this.formValues;
    return `<modal-dialog id="user-modal" title="${title}" confirm-label="${lbl}" ${show ? 'open' : ''}>
      ${this.formError ? `<p class="form-error">\u26a0 ${this.formError}</p>` : ''}
      <form id="user-form" class="form-grid" autocomplete="off">
        <div class="field"><label for="fe">Email</label>
          <input id="fe" type="email" name="email" value="${v.email}" required></div>
        <div class="field"><label for="fd">Display Name</label>
          <input id="fd" type="text" name="displayName" value="${v.displayName}" required></div>
        <div class="field"><label for="fr">Role</label>
          <select id="fr" name="role" required>
            <option value="${UserRole.BUYER}" ${v.role === UserRole.BUYER ? 'selected' : ''}>Buyer</option>
            <option value="${UserRole.SELLER}" ${v.role === UserRole.SELLER ? 'selected' : ''}>Seller</option>
            <option value="${UserRole.ADMIN}" ${v.role === UserRole.ADMIN ? 'selected' : ''}>Admin</option>
          </select>
        </div>
      </form></modal-dialog>`;
  }

  private deleteModalTpl(): string {
    if (this.modal.kind !== 'delete')
      return `<modal-dialog id="delete-modal" title="Confirm Delete" confirm-label="Delete"></modal-dialog>`;
    const { user } = this.modal;
    return `<modal-dialog id="delete-modal" title="Confirm Delete" confirm-label="Delete" open>
      <p class="modal-help">
        Delete user <strong>${user.displayName}</strong>? This action cannot be undone.</p>
      </modal-dialog>`;
  }

  private accountModalsTpl(): string {
    return this.createAccountModalTpl() + this.topUpAccountModalTpl() + this.deleteAccountModalTpl();
  }

  private createAccountModalTpl(): string {
    const open = this.modal.kind === 'create-account';
    const lbl = this.submitting ? 'Creating…' : 'Create';
    return `<modal-dialog id="create-account-modal" title="Add Account" confirm-label="${lbl}" ${open ? 'open' : ''}>
      ${this.formError ? `<p class="form-error">${this.formError}</p>` : ''}
      <form class="form-grid" autocomplete="off">
        <div class="field"><label for="an">Account Name <span class="req">*</span></label>
          <input id="an" type="text" name="accountName" placeholder="e.g. Primary" required></div>
      </form>
    </modal-dialog>`;
  }

  private topUpAccountModalTpl(): string {
    if (this.modal.kind !== 'topup-account')
      return `<modal-dialog id="topup-modal" title="Top Up Account" confirm-label="Top Up"></modal-dialog>`;
    const { account } = this.modal;
    const lbl = this.submitting ? 'Processing…' : 'Top Up';
    return `<modal-dialog id="topup-modal" title="Top Up Account" confirm-label="${lbl}" open>
      ${this.formError ? `<p class="form-error">${this.formError}</p>` : ''}
      <p class="modal-help">Account: <strong>${account.name}</strong> (Current balance: ${account.balance})</p>
      <form class="form-grid" autocomplete="off">
        <div class="field"><label for="ta">Amount <span class="req">*</span></label>
          <input id="ta" type="number" step="0.01" name="topupAmount" placeholder="e.g. 100.00" required></div>
      </form>
    </modal-dialog>`;
  }

  private deleteAccountModalTpl(): string {
    if (this.modal.kind !== 'delete-account')
      return `<modal-dialog id="delete-account-modal" title="Confirm Delete" confirm-label="Delete"></modal-dialog>`;
    const { account } = this.modal;
    return `<modal-dialog id="delete-account-modal" title="Confirm Delete" confirm-label="Delete" open>
      <p class="modal-help">Delete account <strong>${account.name}</strong> (Balance: ${account.balance})? This action cannot be undone.</p>
    </modal-dialog>`;
  }

  private bindEvents(): void {
    this.querySelector('[data-action="create"]')?.addEventListener('click', () => {
      this.formValues = { email: '', displayName: '', role: UserRole.BUYER };
      this.formError = null; this.modal = { kind: 'create' }; this.render();
    });

    this.querySelector('[data-action="reload"]')?.addEventListener('click', () => void this.loadUsers());

    this.querySelectorAll<HTMLElement>('[data-action="edit"]').forEach(btn =>
      btn.addEventListener('click', async () => {
        const id = parseInt(btn.dataset['id'] ?? '0', 10);
        try {
          const user = await getUserById(id);
          this.formValues = { email: user.email, displayName: user.displayName, role: user.role };
          this.formError = null; this.modal = { kind: 'edit', user }; this.render();
        } catch { this.toastEl.show('Failed to load user.', 'error'); }
      }));

    this.querySelectorAll<HTMLElement>('[data-action="delete"]').forEach(btn =>
      btn.addEventListener('click', () => {
        const id = parseInt(btn.dataset['id'] ?? '0', 10);
        const user = this.users.find(u => u.id === id);
        if (user) { this.modal = { kind: 'delete', user }; this.render(); }
      }));

    this.querySelectorAll<HTMLElement>('[data-action="toggle"]').forEach(btn =>
      btn.addEventListener('click', async () => {
        const id = parseInt(btn.dataset['id'] ?? '0', 10);
        if (this.expandedUserId === id) {
          this.expandedUserId = null; this.expandedUser = null; this.accounts = []; this.render();
        } else {
          try {
            this.expandedUser = await getUserById(id); this.expandedUserId = id;
            this.render();
            await this.loadAccounts(id);
          } catch { this.toastEl.show('Failed to load user details.', 'error'); }
        }
      }));

    this.querySelector('#user-modal')?.addEventListener('dialog-confirm', () => void this.handleFormSubmit());
    this.querySelector('#user-modal')?.addEventListener('dialog-cancel', () => { this.modal = { kind: 'none' }; this.formError = null; });
    this.querySelector('#delete-modal')?.addEventListener('dialog-confirm', () => void this.handleDeleteConfirm());
    this.querySelector('#delete-modal')?.addEventListener('dialog-cancel', () => { this.modal = { kind: 'none' }; });

    // Account action handlers
    this.querySelectorAll<HTMLElement>('[data-action="create-account"]').forEach(btn =>
      btn.addEventListener('click', () => {
        const userId = parseInt(btn.dataset['userId'] ?? '0', 10);
        this.formError = null;
        this.modal = { kind: 'create-account', userId };
        this.render();
      }));

    this.querySelectorAll<HTMLElement>('[data-action="topup-account"]').forEach(btn =>
      btn.addEventListener('click', () => {
        const userId = parseInt(btn.dataset['userId'] ?? '0', 10);
        const accountId = parseInt(btn.dataset['accountId'] ?? '0', 10);
        const account = this.accounts.find(a => a.id === accountId);
        if (account) {
          this.formError = null;
          this.modal = { kind: 'topup-account', userId, account };
          this.render();
        }
      }));

    this.querySelectorAll<HTMLElement>('[data-action="delete-account"]').forEach(btn =>
      btn.addEventListener('click', () => {
        const userId = parseInt(btn.dataset['userId'] ?? '0', 10);
        const accountId = parseInt(btn.dataset['accountId'] ?? '0', 10);
        const account = this.accounts.find(a => a.id === accountId);
        if (account) {
          this.modal = { kind: 'delete-account', userId, account };
          this.render();
        }
      }));

    this.querySelector('#create-account-modal')?.addEventListener('dialog-confirm', () => void this.handleCreateAccount());
    this.querySelector('#create-account-modal')?.addEventListener('dialog-cancel', () => { this.modal = { kind: 'none' }; this.formError = null; });
    this.querySelector('#topup-modal')?.addEventListener('dialog-confirm', () => void this.handleTopUpAccount());
    this.querySelector('#topup-modal')?.addEventListener('dialog-cancel', () => { this.modal = { kind: 'none' }; this.formError = null; });
    this.querySelector('#delete-account-modal')?.addEventListener('dialog-confirm', () => void this.handleDeleteAccount());
    this.querySelector('#delete-account-modal')?.addEventListener('dialog-cancel', () => { this.modal = { kind: 'none' }; });

    this.querySelector('pagination-bar')?.addEventListener('page-change', e => {
      const { page, size } = (e as CustomEvent<{ page: number; size: number }>).detail;
      this.page = page; this.size = size;
      this.expandedUserId = null; this.expandedUser = null;
      void this.loadUsers();
    });
    this.querySelector('pagination-bar')?.addEventListener('size-change', e => {
      const { size } = (e as CustomEvent<{ page: number; size: number }>).detail;
      this.page = 0; this.size = size;
      this.expandedUserId = null; this.expandedUser = null;
      void this.loadUsers();
    });
  }
}

if (!customElements.get('users-page')) {
  customElements.define('users-page', UsersPageElement);
}
