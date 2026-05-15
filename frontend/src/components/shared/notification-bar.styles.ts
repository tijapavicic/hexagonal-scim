export const NOTIFICATION_BAR_VISIBLE_STYLES = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :host {
    position: fixed;
    top: 20px;
    right: 20px;
    z-index: 9999;
    font-family: system-ui, sans-serif;
  }
  .toast {
    padding: 12px 20px;
    border-radius: 12px;
    font-size: 14px;
    font-weight: 600;
    border: 1px solid transparent;
    box-shadow: 0 12px 24px rgba(15,23,42,0.16);
    animation: slide-in 0.22s cubic-bezier(0.22, 1, 0.36, 1);
  }
  @keyframes slide-in {
    from { transform: translateX(24px); opacity: 0; }
    to   { transform: translateX(0);    opacity: 1; }
  }
  .success { background: #ecfdf5; color: #14532d; border-color: #bbf7d0; }
  .error   { background: #fef2f2; color: #7f1d1d; border-color: #fecaca; }
  @media (prefers-reduced-motion: reduce) { .toast { animation: none; } }
`;

export const NOTIFICATION_BAR_HIDDEN_STYLES =
  ':host { position: fixed; top: 20px; right: 20px; z-index: 9999; pointer-events: none; }';

