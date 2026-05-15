export const PAGINATION_BAR_STYLES = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :host {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 0;
    font-family: inherit;
    font-size: 13px;
  }
  button {
    padding: 7px 14px;
    border: 1px solid var(--scim-border, #d1d5db);
    border-radius: 10px;
    background: var(--scim-surface, #fff);
    cursor: pointer;
    font-size: 13px;
    font-weight: 600;
    transition: background 0.14s, transform 0.14s, border-color 0.14s;
  }
  button:not(:disabled):hover {
    background: var(--scim-surface-muted, #f3f4f6);
    border-color: color-mix(in srgb, var(--scim-primary, #2563eb) 28%, var(--scim-border, #d1d5db));
    transform: translateY(-1px);
  }
  button:disabled { opacity: 0.45; cursor: default; }
  .info { color: var(--scim-text-muted, #4b5563); font-weight: 600; }
  select {
    padding: 6px 10px;
    border: 1px solid var(--scim-border, #d1d5db);
    border-radius: 10px;
    font-size: 13px;
    background: var(--scim-surface, #fff);
    cursor: pointer;
  }
  button:focus-visible, select:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--scim-primary, #2563eb) 65%, white);
    outline-offset: 2px;
  }
  @media (prefers-reduced-motion: reduce) {
    button, select { transition: none; }
  }
`;

