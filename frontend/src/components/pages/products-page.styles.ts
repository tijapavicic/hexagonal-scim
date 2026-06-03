export const PRODUCTS_PAGE_STYLES = `<style>
  :host {
    display: block;
    font-family: system-ui, -apple-system, sans-serif;
  }

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 1.5rem;
    padding:0 0.5rem;
  }

  h2 {
    margin: 0;
    font-size: 1.75rem;
    font-weight: 600;
    color: #111827;
  }

  .card {
    background: white;
    border: 1px solid #e5e7eb;
    border-radius: 0.5rem;
    overflow: hidden;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.9rem;
  }

  thead {
    background: #f9fafb;
    border-bottom: 1px solid #e5e7eb;
  }

  th {
    padding: 0.75rem 1rem;
    text-align: left;
    font-weight: 600;
    color: #374151;
    font-size: 0.85rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  td {
    padding: 0.75rem 1rem;
    border-bottom: 1px solid #f3f4f6;
    color: #1f2937;
  }

  tbody tr.product-row:hover {
    background: #fafafa;
  }

  .btn-ghost {
    background: none;
    border: none;
    color: #2563eb;
    cursor: pointer;
    font-weight: 500;
    text-align: left;
    padding: 0;
  }

  .btn-ghost:hover {
    color: #1d4ed8;
    text-decoration: underline;
  }

  .detail-row td {
    background: #f9fafb;
    border-bottom: 1px solid #e5e7eb;
    padding: 1.5rem;
  }

  .detail-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 1rem;
  }

  .dl {
    font-size: 0.8rem;
    font-weight: 600;
    color: #6b7280;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 0.25rem;
  }

  .dv {
    font-size: 0.95rem;
    color: #111827;
    word-break: break-word;
  }

  .actions {
    display: flex;
    gap: 0.5rem;
  }

  .btn {
    padding: 0.5rem 1rem;
    border: none;
    border-radius: 0.375rem;
    font-size: 0.875rem;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s;
  }

  .btn-primary {
    background: #2563eb;
    color: white;
  }

  .btn-primary:hover {
    background: #1d4ed8;
  }

  .btn-secondary {
    background: #6b7280;
    color: white;
  }

  .btn-secondary:hover {
    background: #4b5563;
  }

  .btn-danger {
    background: #dc2626;
    color: white;
  }

  .btn-danger:hover {
    background: #b91c1c;
  }

  .loading-state {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 3rem;
    color: #6b7280;
    font-size: 0.95rem;
  }

  .spinner {
    width: 24px;
    height: 24px;
    border: 3px solid #e5e7eb;
    border-top-color: #2563eb;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    margin-right: 0.75rem;
  }

  @keyframes spin {
    to { transform: rotate(360deg); }
  }

  .state-msg {
    padding: 2rem;
    text-align: center;
    color: #6b7280;
  }

  .error-msg {
    color: #dc2626;
  }

  .form-grid {
    display: grid;
    gap: 1rem;
  }

  .field {
    display: flex;
    flex-direction: column;
    gap: 0.375rem;
  }

  .field-full {
    grid-column: 1 / -1;
  }

  label {
    font-size: 0.875rem;
    font-weight: 600;
    color: #374151;
  }

  .req {
    color: #dc2626;
  }

  input, select, textarea {
    padding: 0.5rem 0.75rem;
    border: 1px solid #d1d5db;
    border-radius: 0.375rem;
    font-size: 0.9rem;
    font-family: inherit;
  }

  input:focus, select:focus, textarea:focus {
    outline: none;
    border-color: #2563eb;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
  }

  textarea {
    resize: vertical;
  }

  .form-error {
    background: #fef2f2;
    color: #dc2626;
    padding: 0.75rem;
    border-radius: 0.375rem;
    margin: 0 0 1rem 0;
    font-size: 0.875rem;
    border-left: 4px solid #dc2626;
  }

  .modal-help {
    color: #6b7280;
    margin: 0;
    line-height: 1.6;
  }
</style>`;

