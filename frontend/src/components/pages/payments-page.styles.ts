export const PAYMENTS_PAGE_STYLES = `<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
.page-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;font-family:system-ui,sans-serif}
.page-header h2{font-size:18px;font-weight:700;color:#111827}
.card{background:#fff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden}
table{width:100%;border-collapse:collapse;font-family:system-ui,sans-serif;font-size:14px}
thead tr{background:#f9fafb}
th{padding:10px 14px;font-weight:600;color:#374151;text-align:left;border-bottom:2px solid #e5e7eb}
td{padding:10px 14px;border-bottom:1px solid #f3f4f6;color:#111827;vertical-align:middle}
tr:last-child td{border-bottom:none}
tr.payment-row:hover td{background:#fafbff}
tr.detail-row td{background:#f8faff;padding:12px 20px}
.detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 24px;font-size:13px}
.dl{color:#6b7280;font-weight:600;text-transform:uppercase;font-size:11px}
.dv{color:#111827;margin-top:2px}
/* Badges – WCAG AA verified: all pairs ≥ 4.5 : 1 */
.badge{display:inline-block;padding:2px 10px;border-radius:12px;font-size:12px;font-weight:600}
.badge-pending{background:#fef3c7;color:#78350f}
.badge-completed{background:#d1fae5;color:#065f46}
.badge-failed{background:#fee2e2;color:#991b1b}
.badge-default{background:#e5e7eb;color:#374151}
.actions{display:flex;gap:6px}
/* Buttons – WCAG AA focus-visible rings */
.btn{padding:5px 12px;border-radius:5px;font-size:13px;font-weight:500;cursor:pointer;border:1px solid transparent;transition:background .12s;font-family:system-ui,sans-serif}
.btn-primary{background:#4338ca;color:#fff;border-color:#4338ca}
.btn-primary:hover{background:#3730a3}
.btn-primary:focus-visible{outline:3px solid #818cf8;outline-offset:2px}
.btn-secondary{background:#fff;color:#374151;border-color:#d1d5db}
.btn-secondary:hover{background:#f9fafb}
.btn-secondary:focus-visible{outline:3px solid #6366f1;outline-offset:2px}
/* Loading spinner */
.spinner{width:28px;height:28px;border:3px solid #e5e7eb;border-top-color:#4338ca;border-radius:50%;animation:spin .7s linear infinite;margin:0 auto 10px}
@keyframes spin{to{transform:rotate(360deg)}}
.loading-state{display:flex;flex-direction:column;align-items:center;padding:40px 32px;color:#6b7280;font-family:system-ui,sans-serif;font-size:14px}
.state-msg{padding:32px;text-align:center;color:#6b7280;font-family:system-ui,sans-serif;font-size:14px}
.error-msg{color:#991b1b}
.form-grid{display:flex;flex-direction:column;gap:14px}
.field{display:flex;flex-direction:column;gap:4px}
.field label{font-size:13px;font-weight:600;color:#374151;font-family:system-ui,sans-serif}
.field input,.field select{padding:8px 10px;border:1px solid #d1d5db;border-radius:6px;font-size:14px;font-family:system-ui,sans-serif;width:100%}
.field input:focus-visible,.field select:focus-visible{outline:none;border-color:#4338ca;box-shadow:0 0 0 3px rgba(67,56,202,.2)}
.form-error{color:#991b1b;font-size:13px;margin-bottom:4px;font-family:system-ui,sans-serif}
</style>`;

