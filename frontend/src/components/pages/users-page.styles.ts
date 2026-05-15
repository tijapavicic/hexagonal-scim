export const USERS_PAGE_STYLES = `<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
.page-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px;font-family:inherit}
.page-header h2{font-size:clamp(20px,2vw,24px);font-weight:800;letter-spacing:-.01em;color:var(--scim-text,#0f172a)}
.card{background:var(--scim-surface,#fff);border:1px solid var(--scim-border,#e5e7eb);border-radius:16px;overflow:hidden;box-shadow:var(--scim-shadow-sm,0 1px 2px rgba(15,23,42,.06));animation:card-in var(--scim-dur-med,220ms) var(--scim-ease,ease-out)}
table{width:100%;border-collapse:collapse;font-family:inherit;font-size:14px}
thead tr{background:var(--scim-surface-muted,#f9fafb)}
th{padding:12px 16px;font-weight:700;color:var(--scim-text-muted,#475569);text-align:left;border-bottom:1px solid var(--scim-border,#e5e7eb)}
td{padding:11px 16px;border-bottom:1px solid #eef2f7;color:var(--scim-text,#0f172a);vertical-align:middle}
tr:last-child td{border-bottom:none}
tr.user-row:hover td{background:#f8fbff}
tr.detail-row td{background:#f8faff;padding:14px 20px}
.detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px 24px;font-size:13px}
.dl{color:var(--scim-text-muted,#6b7280);font-weight:700;text-transform:uppercase;font-size:11px}
.dv{color:var(--scim-text,#0f172a);margin-top:2px}
.badge{display:inline-block;padding:3px 10px;border-radius:999px;font-size:12px;font-weight:700}
.badge-active{background:#dcfce7;color:#14532d}
.badge-inactive{background:#fee2e2;color:#7f1d1d}
.actions{display:flex;gap:8px}
.btn{padding:7px 13px;border-radius:10px;font-size:13px;font-weight:600;cursor:pointer;border:1px solid transparent;transition:background var(--scim-dur-fast,140ms) var(--scim-ease,ease-out),transform var(--scim-dur-fast,140ms) var(--scim-ease,ease-out),border-color var(--scim-dur-fast,140ms) var(--scim-ease,ease-out);font-family:inherit}
.btn:hover{transform:translateY(-1px)}
.btn-primary{background:var(--scim-primary,#2563eb);color:#fff;border-color:var(--scim-primary,#2563eb)}
.btn-primary:hover{background:var(--scim-primary-strong,#1d4ed8)}
.btn-primary:focus-visible{outline:3px solid color-mix(in srgb,var(--scim-primary,#2563eb) 42%, white);outline-offset:2px}
.btn-secondary{background:#fff;color:#334155;border-color:var(--scim-border,#d1d5db)}
.btn-secondary:hover{background:var(--scim-surface-muted,#f8fafc);border-color:#cbd5e1}
.btn-secondary:focus-visible{outline:3px solid color-mix(in srgb,var(--scim-primary,#2563eb) 30%, white);outline-offset:2px}
.btn-danger{background:#dc2626;color:#fff;border-color:#dc2626}
.btn-danger:hover{background:#b91c1c}
.btn-danger:focus-visible{outline:3px solid #fca5a5;outline-offset:2px}
.btn-ghost{background:none;border:none;color:var(--scim-primary-strong,#1d4ed8);cursor:pointer;font-size:13px;text-decoration:underline;padding:0;font-family:inherit;font-weight:600}
.btn-ghost:hover{color:#1e40af}
.btn-ghost:focus-visible{outline:3px solid color-mix(in srgb,var(--scim-primary,#2563eb) 30%, white);outline-offset:2px;border-radius:4px}
.spinner{width:30px;height:30px;border:3px solid color-mix(in srgb,var(--scim-primary,#2563eb) 20%, #fff);border-top-color:var(--scim-primary,#2563eb);border-radius:50%;animation:spin .9s linear infinite;margin:0 auto 10px}
@keyframes spin{to{transform:rotate(360deg)}}
@keyframes card-in{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:translateY(0)}}
.loading-state{display:flex;flex-direction:column;align-items:center;padding:42px 32px;color:var(--scim-text-muted,#6b7280);font-family:inherit;font-size:14px}
.state-msg{padding:34px;text-align:center;color:var(--scim-text-muted,#6b7280);font-family:inherit;font-size:14px}
.error-msg{color:#b91c1c}
.form-grid{display:flex;flex-direction:column;gap:14px}
.field{display:flex;flex-direction:column;gap:5px}
.field label{font-size:13px;font-weight:700;color:var(--scim-text-muted,#475569);font-family:inherit}
.field input{padding:9px 11px;border:1px solid var(--scim-border,#d1d5db);border-radius:10px;font-size:14px;font-family:inherit;width:100%;background:#fff;transition:border-color var(--scim-dur-fast,140ms) var(--scim-ease,ease-out),box-shadow var(--scim-dur-fast,140ms) var(--scim-ease,ease-out)}
.field input:focus-visible{outline:none;border-color:var(--scim-primary,#2563eb);box-shadow:0 0 0 3px color-mix(in srgb,var(--scim-primary,#2563eb) 22%, transparent)}
.cbrow{display:flex;align-items:center;gap:8px;font-family:inherit;font-size:14px}
.modal-help{font-family:inherit;font-size:14px;color:#374151}
.form-error{color:#b91c1c;font-size:13px;margin-bottom:4px;font-family:inherit}
@media (prefers-reduced-motion: reduce){.card,.btn,.spinner{animation:none;transition:none}}
</style>`;
