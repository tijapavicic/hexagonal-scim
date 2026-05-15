export const HOME_PAGE_STYLES = `
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :host { display: block; font-family: inherit; color: var(--scim-text, #0f172a); }

  .welcome {
    background: var(--scim-surface, #fff);
    border: 1px solid var(--scim-border, #e5e7eb);
    border-radius: 16px;
    padding: 24px;
    margin-bottom: 20px;
    box-shadow: var(--scim-shadow-sm, 0 1px 2px rgba(15,23,42,.06));
    animation: card-in var(--scim-dur-med, 220ms) var(--scim-ease, ease-out);
  }
  .welcome h2 { font-size: clamp(22px, 2.2vw, 28px); font-weight: 800; letter-spacing: -0.01em; color: var(--scim-text, #0f172a); margin-bottom: 6px; }
  .welcome p  { color: var(--scim-text-muted, #4b5563); font-size: 14px; }

  .cards { display: flex; gap: 16px; flex-wrap: wrap; }

  .card {
    background: var(--scim-surface, #fff);
    border: 1px solid var(--scim-border, #e5e7eb);
    border-radius: 16px;
    padding: 20px;
    min-width: 200px;
    flex: 1;
    box-shadow: var(--scim-shadow-sm, 0 1px 2px rgba(15,23,42,.06));
    animation: card-in var(--scim-dur-med, 220ms) var(--scim-ease, ease-out);
  }
  .card.link-card {
    text-decoration: none;
    color: inherit;
    cursor: pointer;
    transition: box-shadow var(--scim-dur-fast, 140ms) var(--scim-ease, ease-out),
                transform var(--scim-dur-fast, 140ms) var(--scim-ease, ease-out),
                border-color var(--scim-dur-fast, 140ms) var(--scim-ease, ease-out);
    display: block;
  }
  .card.link-card:hover {
    box-shadow: var(--scim-shadow-md, 0 12px 32px rgba(15,23,42,.12));
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--scim-primary, #2563eb) 28%, var(--scim-border, #e5e7eb));
  }
  .card.link-card:focus-visible { outline: 2px solid color-mix(in srgb, var(--scim-primary, #2563eb) 68%, white); outline-offset: 2px; }

  .card-icon  { font-size: 28px; margin-bottom: 10px; filter: saturate(1.1); }
  .card-label { font-size: 12px; font-weight: 700; color: var(--scim-text-muted, #6b7280); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 4px; }
  .card-value { font-size: 30px; font-weight: 800; color: var(--scim-text, #0f172a); margin-bottom: 8px; letter-spacing: -0.02em; }
  .card-value-sm { font-size: 18px; }
  .card-link  { font-size: 13px; color: var(--scim-primary-strong, #1d4ed8); font-weight: 700; }

  .skeleton {
    display: inline-block;
    width: 48px;
    height: 28px;
    border-radius: 8px;
    background: linear-gradient(90deg, #e5e7eb 25%, #f3f4f6 50%, #e5e7eb 75%);
    background-size: 200% 100%;
    animation: shimmer 1.2s infinite;
    vertical-align: middle;
  }
  @keyframes shimmer { 0% { background-position: -200% 0; } 100% { background-position: 200% 0; } }
  @keyframes card-in {
    from { opacity: 0; transform: translateY(6px); }
    to   { opacity: 1; transform: translateY(0); }
  }

  .error-chip {
    font-size: 13px;
    color: #b91c1c;
    font-weight: 500;
  }

  @media (prefers-reduced-motion: reduce) {
    .welcome, .card, .card.link-card, .skeleton { animation: none; transition: none; }
  }
`;

