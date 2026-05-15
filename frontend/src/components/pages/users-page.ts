import { listUsers, getUserById, createUser, updateUser, deleteUser } from '../../api/users';
import { getAuthProfile } from '../../auth/keycloak';
import type { UserDto, CreateUserDto } from '../../types/user.dto';
import type { NotificationBarElement } from '../shared/notification-bar';
import '../shared/notification-bar';
import '../shared/pagination-bar';
import '../shared/modal-dialog';

interface FormValues {
  username: string; email: string; firstName: string; lastName: string; active: boolean;
}

type ModalState =
  | { kind: 'none' }
  | { kind: 'create' }
  | { kind: 'edit'; user: UserDto }
  | { kind: 'delete'; user: UserDto };

const STYLES = `<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
.page-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;font-family:system-ui,sans-serif}
.page-header h2{font-size:18px;font-weight:700;color:#111827}
.card{background:#fff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden}
table{width:100%;border-collapse:collapse;font-family:system-ui,sans-serif;font-size:14px}
thead tr{background:#f9fafb}
th{padding:10px 14px;font-weight:600;color:#374151;text-align:left;border-bottom:2px solid #e5e7eb}
td{padding:10px 14px;border-bottom:1px solid #f3f4f6;color:#111827;vertical-align:middle}
tr:last-child td{border-bottom:none}
tr.user-row:hover td{background:#fafbff}
tr.detail-row td{background:#f8faff;padding:12px 20px}
.detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 24px;font-size:13px}
.dl{color:#6b7280;font-weight:600;text-transform:uppercase;font-size:11px}
.dv{color:#111827;margin-top:2px}
.badge{display:inline-block;padding:2px 10px;border-radius:12px;font-size:12px;font-weight:600}
.badge-active{background:#d1fae5;color:#065f46}
.badge-inactive{background:#fee2e2;color:#991b1b}
.actions{display:flex;gap:6px}
.btn{padding:5px 12px;border-radius:5px;font-size:13px;font-weight:500;cursor:pointer;border:1px solid transparent;transition:background .12s;font-family:system-ui,sans-serif}
.btn-primary{background:#4f46e5;color:#fff;border-color:#4f46e5}
.btn-primary:hover{background:#4338ca}
.btn-secondary{background:#fff;color:#374151;border-color:#d1d5db}
.btn-secondary:hover{background:#f9fafb}
.btn-danger{background:#dc2626;color:#fff;border-color:#dc2626}
.btn-danger:hover{background:#b91c1c}
.btn-ghost{background:none;border:none;color:#4f46e5;cursor:pointer;font-size:13px;text-decoration:underline;padding:0;font-family:system-ui,sans-serif}
.btn-ghost:hover{color:#4338ca}
.state-msg{padding:32px;text-align:center;color:#6b7280;font-family:system-ui,sans-serif;font-size:14px}
.error-msg{color:#b91c1c}
.form-grid{display:flex;flex-direction:column;gap:14px}
.field{display:flex;flex-direction:column;gap:4px}
.field label{font-size:13px;font-weight:600;color:#374151;font-family:system-ui,sans-serif}
.field input{padding:8px 10px;border:1px solid #d1d5db;border-radius:6px;font-size:14px;font-family:system-ui,sans-serif;width:100%}
.field input:focus{outline:none;border-color:#4f46e5;box-shadow:0 0 0 3px rgba(79,70,229,.12)}
.cbrow{display:flex;align-items:center;gap:8px;font-family:system-ui,sans-serif;font-size:14px}
.form-error{color:#b91c1c;font-size:13px;margin-bottom:4px;font-family:system-ui,sans-serif}
</style>`;

class UsersPageElement extends HTMLElement {
  private users: UserDto[] = [];
  private page = 0; private size = 10; private hasMore = false;
  private loading = false; private listError: string | null = null;
  private expandedUserId: number | null = null; private expandedUser: UserDto | null = null;
  private modal: ModalState = { kind: 'none' };
  private formValues: FormValues = { username: '', email: '', firstName: '', lastName: '', active: true };
  private formError: string | null = null; private submitting = false;
  private isAdmin = false;
  private toastEl!: NotificationBarElement;

  connectedCallback(): void {
    this.isAdmin = getAuthProfile().realmRoles.some(r => r.toUpperCase() === 'ADMIN');
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
      username:  (f.querySelector<HTMLInputElement>('[name="username"]')?.value ?? '').trim(),
      email:     (f.querySelector<HTMLInputElement>('[name="email"]')?.value ?? '').trim(),
      firstName: (f.querySelector<HTMLInputElement>('[name="firstName"]')?.value ?? '').trim(),
      lastName:  (f.querySelector<HTMLInputElement>('[name="lastName"]')?.value ?? '').trim(),
      active:    f.querySelector<HTMLInputElement>('[name="active"]')?.checked ?? true,
    };
  }

  private validate(v: FormValues): string | null {
    if (!v.username)  return 'Username is required.';
    if (!v.email)     return 'Email is required.';
    if (!v.firstName) return 'First name is required.';
    if (!v.lastName)  return 'Last name is required.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.email)) return 'Enter a valid email address.';
    return null;
  }

  private async handleFormSubmit(): Promise<void> {
    const v = this.readForm(); if (!v) return;
    const err = this.validate(v);
    if (err) { this.formError = err; this.render(); return; }
    this.formValues = v; this.submitting = true; this.formError = null; this.render();
    const body: CreateUserDto = { username: v.username, email: v.email, firstName: v.firstName, lastName: v.lastName, active: v.active };
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
      this.toastEl.show(`User "${user.username}" deleted.`, 'success');
      await this.loadUsers();
    } catch (e) {
      this.modal = { kind: 'none' };
      this.toastEl.show(e instanceof Error ? e.message : 'Delete failed.', 'error');
      this.render();
    }
  }

  private render(): void {
    const main = this.querySelector<HTMLElement>('#main'); if (!main) return;
    main.innerHTML = STYLES + this.mainTpl();
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
      ${this.deleteModalTpl()}`;
  }

  private listContent(): string {
    if (this.loading) return '<div class="state-msg">Loading users\u2026</div>';
    if (this.listError) return `<div class="state-msg error-msg">${this.listError}<br><br>
      <button class="btn btn-secondary" data-action="reload">\u21ba Retry</button></div>`;
    if (!this.users.length) return '<div class="state-msg">No users found.</div>';
    const rows = this.users.flatMap(u => {
      const detail = this.expandedUserId === u.id && this.expandedUser
        ? `<tr class="detail-row"><td colspan="6">${this.detailTpl(this.expandedUser)}</td></tr>` : '';
      return `<tr class="user-row">
        <td>${u.id}</td>
        <td><button class="btn-ghost" data-action="toggle" data-id="${u.id}">${u.username}</button></td>
        <td>${u.email}</td><td>${u.firstName} ${u.lastName}</td>
        <td><span class="badge ${u.active ? 'badge-active' : 'badge-inactive'}">${u.active ? 'Active' : 'Inactive'}</span></td>
        <td><div class="actions">${this.isAdmin ? `
          <button class="btn btn-secondary" data-action="edit"   data-id="${u.id}">Edit</button>
          <button class="btn btn-danger"    data-action="delete" data-id="${u.id}">Delete</button>` : ''}</div></td>
      </tr>${detail}`;
    });
    return `<table><thead><tr>
      <th>ID</th><th>Username</th><th>Email</th><th>Full Name</th><th>Status</th><th>Actions</th>
      </tr></thead><tbody>${rows.join('')}</tbody></table>`;
  }

  private detailTpl(u: UserDto): string {
    const f = (label: string, val: string) => `<div><div class="dl">${label}</div><div class="dv">${val}</div></div>`;
    return `<div class="detail-grid">
      ${f('ID', String(u.id))}${f('Username', u.username)}${f('Email', u.email)}
      ${f('First Name', u.firstName)}${f('Last Name', u.lastName)}
      <div><div class="dl">Status</div><div class="dv">
        <span class="badge ${u.active ? 'badge-active' : 'badge-inactive'}">${u.active ? 'Active' : 'Inactive'}</span>
      </div></div></div>`;
  }

  private userModalTpl(): string {
    const show = this.modal.kind === 'create' || this.modal.kind === 'edit';
    const title = this.modal.kind === 'edit' ? 'Edit User' : 'Create User';
    const lbl = this.submitting ? 'Saving\u2026' : 'Save';
    const v = this.formValues;
    return `<modal-dialog id="user-modal" title="${title}" confirm-label="${lbl}" ${show ? 'open' : ''}>
      ${this.formError ? `<p class="form-error">\u26a0 ${this.formError}</p>` : ''}
      <form id="user-form" class="form-grid" autocomplete="off">
        <div class="field"><label for="fu">Username</label>
          <input id="fu" type="text" name="username" value="${v.username}" required></div>
        <div class="field"><label for="fe">Email</label>
          <input id="fe" type="email" name="email" value="${v.email}" required></div>
        <div class="field"><label for="ff">First Name</label>
          <input id="ff" type="text" name="firstName" value="${v.firstName}" required></div>
        <div class="field"><label for="fl">Last Name</label>
          <input id="fl" type="text" name="lastName" value="${v.lastName}" required></div>
        <div class="cbrow">
          <input id="fa" type="checkbox" name="active" ${v.active ? 'checked' : ''}>
          <label for="fa">Active</label></div>
      </form></modal-dialog>`;
  }

  private deleteModalTpl(): string {
    if (this.modal.kind !== 'delete')
      return `<modal-dialog id="delete-modal" title="Confirm Delete" confirm-label="Delete"></modal-dialog>`;
    const { user } = this.modal;
    return `<modal-dialog id="delete-modal" title="Confirm Delete" confirm-label="Delete" open>
      <p style="font-family:system-ui,sans-serif;font-size:14px;color:#374151;">
        Delete user <strong>${user.username}</strong>? This action cannot be undone.</p>
      </modal-dialog>`;
  }

  private bindEvents(): void {
    this.querySelector('[data-action="create"]')?.addEventListener('click', () => {
      this.formValues = { username: '', email: '', firstName: '', lastName: '', active: true };
      this.formError = null; this.modal = { kind: 'create' }; this.render();
    });

    this.querySelector('[data-action="reload"]')?.addEventListener('click', () => void this.loadUsers());

    this.querySelectorAll<HTMLElement>('[data-action="edit"]').forEach(btn =>
      btn.addEventListener('click', async () => {
        const id = parseInt(btn.dataset['id'] ?? '0', 10);
        try {
          const user = await getUserById(id);
          this.formValues = { username: user.username, email: user.email, firstName: user.firstName, lastName: user.lastName, active: user.active };
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
          this.expandedUserId = null; this.expandedUser = null; this.render();
        } else {
          try {
            this.expandedUser = await getUserById(id); this.expandedUserId = id; this.render();
          } catch { this.toastEl.show('Failed to load user details.', 'error'); }
        }
      }));

    this.querySelector('#user-modal')?.addEventListener('dialog-confirm', () => void this.handleFormSubmit());
    this.querySelector('#user-modal')?.addEventListener('dialog-cancel', () => { this.modal = { kind: 'none' }; this.formError = null; });
    this.querySelector('#delete-modal')?.addEventListener('dialog-confirm', () => void this.handleDeleteConfirm());
    this.querySelector('#delete-modal')?.addEventListener('dialog-cancel', () => { this.modal = { kind: 'none' }; });

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
