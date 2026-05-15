export const SCIM_APP_SHELL_STYLES = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :host {
    display: block;
    min-height: 100vh;
    font-family: Inter, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    --scim-bg: #f6f8fc;
    --scim-surface: #ffffff;
    --scim-surface-muted: #f8fafc;
    --scim-text: #0f172a;
    --scim-text-muted: #475569;
    --scim-border: #e2e8f0;
    --scim-primary: #2563eb;
    --scim-primary-strong: #1d4ed8;
    --scim-danger: #dc2626;
    --scim-shadow-sm: 0 1px 2px rgba(15, 23, 42, 0.06);
    --scim-shadow-md: 0 12px 32px rgba(15, 23, 42, 0.12);
    --scim-ease: cubic-bezier(0.22, 1, 0.36, 1);
    --scim-dur-fast: 140ms;
    --scim-dur-med: 220ms;
  }
  .topbar {
    background: color-mix(in srgb, var(--scim-surface) 88%, transparent);
    color: var(--scim-text);
    display: flex;
    align-items: center;
    padding: 0 24px;
    height: 60px;
    gap: 16px;
    position: sticky;
    top: 0;
    z-index: 10;
    border-bottom: 1px solid var(--scim-border);
    box-shadow: var(--scim-shadow-sm);
    backdrop-filter: blur(8px) saturate(1.1);
  }
  .topbar-brand {
    font-size: 17px;
    font-weight: 800;
    margin-right: 8px;
    white-space: nowrap;
    letter-spacing: -0.01em;
  }
  nav { display: flex; gap: 4px; flex: 1; }
  nav a {
    color: var(--scim-text-muted);
    text-decoration: none;
    padding: 7px 14px;
    border-radius: 10px;
    font-size: 14px;
    transition: background var(--scim-dur-fast) var(--scim-ease),
                color var(--scim-dur-fast) var(--scim-ease),
                transform var(--scim-dur-fast) var(--scim-ease);
  }
  nav a:hover { background: var(--scim-surface-muted); color: var(--scim-text); transform: translateY(-1px); }
  nav a.active {
    background: color-mix(in srgb, var(--scim-primary) 14%, white);
    color: var(--scim-primary-strong);
    font-weight: 700;
  }
  nav a:focus-visible { outline: 2px solid color-mix(in srgb, var(--scim-primary) 68%, white); outline-offset: 2px; }
  .profile-chip {
    font-size: 12px;
    color: var(--scim-text-muted);
    white-space: nowrap;
    flex-shrink: 0;
    background: var(--scim-surface-muted);
    border: 1px solid var(--scim-border);
    border-radius: 999px;
    padding: 6px 10px;
  }
  .logout-btn {
    padding: 6px 14px;
    border: 1px solid var(--scim-border);
    border-radius: 10px;
    background: var(--scim-surface);
    color: var(--scim-text);
    cursor: pointer;
    font-size: 13px;
    flex-shrink: 0;
    transition: background var(--scim-dur-fast) var(--scim-ease), transform var(--scim-dur-fast) var(--scim-ease);
  }
  .logout-btn:hover { background: var(--scim-surface-muted); transform: translateY(-1px); }
  .logout-btn:focus-visible { outline: 2px solid color-mix(in srgb, var(--scim-primary) 68%, white); outline-offset: 2px; }
  #page-content {
    padding: 24px;
    background: radial-gradient(circle at top right, #f3f7ff 0%, var(--scim-bg) 46%, #f7f9fd 100%);
    min-height: calc(100vh - 60px);
    animation: page-in var(--scim-dur-med) var(--scim-ease);
  }
  @keyframes page-in {
    from { opacity: .0; transform: translateY(4px); }
    to   { opacity: 1; transform: translateY(0); }
  }
  @media (prefers-reduced-motion: reduce) {
    #page-content, nav a, .logout-btn { animation: none; transition: none; }
  }
`;

export const SCIM_APP_STATUS_STYLES = `
  :host { font-family: Inter, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; color: #0f172a; }
  .status-wrap { max-width: 900px; margin: 56px auto; padding: 0 16px; }
  .status-title { margin-bottom: 8px; font-size: 28px; font-weight: 800; letter-spacing: -0.01em; }
  .status-message { margin-top: 0; color: #475569; font-size: 14px; }
  .status-message.error { color: #b91c1c; }
  .status-btn {
    margin-top: 14px;
    padding: 9px 14px;
    border: 1px solid #d1d5db;
    border-radius: 10px;
    background: #fff;
    cursor: pointer;
    font-weight: 600;
    transition: background 140ms ease, transform 140ms ease;
  }
  .status-btn:hover { background: #f8fafc; transform: translateY(-1px); }
`;

export const SCIM_APP_NOT_FOUND_STYLES = `
  :host { font-family: Inter, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
  .nf-wrap { max-width: 520px; margin: 72px auto; padding: 0 16px; text-align: center; }
  .nf-code { font-size: 64px; margin-bottom: 16px; font-weight: 800; letter-spacing: -0.03em; color: #0f172a; }
  .nf-title { font-size: 24px; font-weight: 800; color: #0f172a; margin-bottom: 8px; }
  .nf-text { color: #64748b; font-size: 14px; margin-bottom: 24px; }
  .nf-link {
    display: inline-block;
    padding: 10px 20px;
    background: #2563eb;
    color: #fff;
    border-radius: 10px;
    text-decoration: none;
    font-size: 14px;
    font-weight: 700;
    transition: background 140ms ease, transform 140ms ease;
  }
  .nf-link:hover { background: #1d4ed8; transform: translateY(-1px); }
`;

