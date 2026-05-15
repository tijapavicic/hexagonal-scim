export const MODAL_DIALOG_STYLES = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :host { font-family: system-ui, sans-serif; }
  .overlay {
    display: none;
    position: fixed;
    inset: 0;
    background: rgba(2, 6, 23, 0.48);
    backdrop-filter: blur(3px);
    z-index: 1000;
    align-items: center;
    justify-content: center;
    animation: overlay-in 140ms cubic-bezier(0.22, 1, 0.36, 1);
  }
  .overlay.open { display: flex; }
  .modal {
    background: #fff;
    border-radius: 16px;
    border: 1px solid #e2e8f0;
    box-shadow: 0 16px 36px rgba(15, 23, 42, 0.2);
    width: 500px;
    max-width: 95vw;
    max-height: 90vh;
    display: flex;
    flex-direction: column;
    animation: dialog-in 220ms cubic-bezier(0.22, 1, 0.36, 1);
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
    color: #6b7280;
    padding: 4px 6px;
    line-height: 1;
    border-radius: 4px;
    transition: color 0.12s, background 0.12s;
  }
  .close-btn:hover { color: #111827; background: #f3f4f6; }
  .close-btn:focus-visible { outline: 3px solid #6366f1; outline-offset: 2px; }
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
    border-radius: 10px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: background 0.14s, transform 0.14s;
  }
  .btn:hover { transform: translateY(-1px); }
  .btn-secondary { background: #fff; color: #374151; border: 1px solid #d1d5db; }
  .btn-secondary:hover { background: #f9fafb; }
  .btn-secondary:focus-visible { outline: 3px solid #6366f1; outline-offset: 2px; }
  .btn-primary { background: #4338ca; color: #fff; border: none; }
  .btn-primary:hover { background: #3730a3; }
  .btn-primary:focus-visible { outline: 3px solid #818cf8; outline-offset: 2px; }
  .btn-danger  { background: #b91c1c; color: #fff; border: none; }
  .btn-danger:hover  { background: #991b1b; }
  .btn-danger:focus-visible  { outline: 3px solid #f87171; outline-offset: 2px; }
  @keyframes overlay-in {
    from { opacity: 0; }
    to { opacity: 1; }
  }
  @keyframes dialog-in {
    from { opacity: 0; transform: translateY(10px) scale(0.98); }
    to { opacity: 1; transform: translateY(0) scale(1); }
  }
  @media (prefers-reduced-motion: reduce) {
    .overlay, .modal, .btn { animation: none; transition: none; }
  }
`;

