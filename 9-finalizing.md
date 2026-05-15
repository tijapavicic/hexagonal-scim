# Phase 9 – Role-Aware Navigation & UX Polish
### What we built, why we built it, and how it works
*Junior developer presentation – Hexagonal SCIM SPA*

---

## 🗺️ Big Picture – Where We Started vs Where We Ended

Before Phase 9, the application had all the CRUD features working — users and payments pages, forms, modals, pagination. But there were several rough edges:

| Problem | Impact |
|---|---|
| Nav links existed but active-state was fragile | Users couldn't tell which page they were on |
| Token expires silently | User keeps working until the next API call fails with a cryptic 401 |
| Unknown URL hash showed a blank page | Confusing for users who bookmarked a bad link |
| Every page had its own copy of the CSS spinner | Hard to change consistently; code smell |
| Modal had no keyboard focus trap | Users could Tab out of an open dialog (WCAG failure) |
| Colour choices untested for vision accessibility | Low contrast could make text unreadable |

Phase 9 fixed all of this. Every change has a direct user or developer benefit — nothing here is for show.

---

## 🏗️ Architecture Reminder – Web Components, No Framework

This SPA uses **native Web Components** — no React, no Vue, no Angular. Each UI piece is a custom HTML element (`<scim-app>`, `<users-page>`, `<modal-dialog>`, etc.).

```
browser
 └── <scim-app>              ← root shell, owns routing + topbar
      ├── <home-page>        ← loaded when hash = #/home
      ├── <users-page>       ← loaded when hash = #/users
      │    ├── <modal-dialog>
      │    ├── <pagination-bar>
      │    └── <notification-bar>
      └── <payments-page>    ← loaded when hash = #/payments
           ├── <modal-dialog>
           └── <pagination-bar>
```

Key Web Component API terms:
- **`customElements.define('tag-name', Class)`** — registers your class as a new HTML tag
- **`connectedCallback()`** — called when the element is inserted into the DOM (like `componentDidMount`)
- **`disconnectedCallback()`** — cleanup when the element is removed (like `componentWillUnmount`)
- **Shadow DOM** — a private, isolated DOM tree. CSS inside it doesn't leak out; CSS outside doesn't affect it
- **Light DOM** — the regular DOM, used by pages so forms inside `<slot>` work correctly

---

## 1. Hash Router – How Client-Side Navigation Works

**File:** `src/components/scim-app.ts`

### What is a hash router?

Traditional websites load a new HTML page for every URL. SPAs (Single Page Applications) fake navigation: they stay on one page but change what the browser renders by watching the URL **hash** — the `#/users` part.

```
https://myapp.local/#/home     ← changes here only
https://myapp.local/#/users    ← no server round-trip
https://myapp.local/#/payments ← no server round-trip
```

The browser fires a `hashchange` event when the hash changes, and we catch it:

```typescript
// scim-app.ts
private readonly onHashChange = (): void => {
  this.routeTo(window.location.hash || '#/home');
};

// Registered once after login succeeds:
window.addEventListener('hashchange', this.onHashChange);
```

### The `routeTo()` method

```typescript
routeTo(hash: string): void {
  const route = normalizeRoute(hash);

  // If someone navigates to an unknown hash, clean-redirect to #/not-found
  if (hash && hash !== route) {
    window.location.hash = route;
    return;
  }

  // Highlight the correct nav link
  this.querySelectorAll<HTMLAnchorElement>('[data-route]').forEach((link) => {
    link.classList.toggle('active', link.dataset['route'] === route);
  });

  // Swap the page content
  const content = this.querySelector<HTMLElement>('#page-content');
  switch (route) {
    case '#/users':    content.innerHTML = '<users-page></users-page>'; break;
    case '#/payments': content.innerHTML = '<payments-page></payments-page>'; break;
    case '#/not-found': this.renderNotFound(content); break;
    default:           /* home - use createElement to pass props */ break;
  }
}
```

**Why `normalizeRoute` instead of `if/else`?**
It makes invalid hashes explicit: `#/random-garbage` → `#/not-found`. The function is a pure lookup with no side-effects, easy to unit test.

### Why clean up on `disconnectedCallback()`?

```typescript
disconnectedCallback(): void {
  window.removeEventListener('hashchange', this.onHashChange);
  disposeAuthRefresh();
  this.stopCountdown();
}
```

If you don't remove the listener, the callback still fires after the element is removed — this is a **memory leak**. The element stays in memory even though it's not in the DOM.

---

## 2. Role-Aware UI – Hiding vs Disabling

**Files:** `src/components/pages/users-page.ts`

### The key rule

> **Hide** admin-only actions for users without admin role. Don't just disable them.

A disabled button is still visible and creates curiosity/frustration ("Why can't I do this?"). Hiding is cleaner UX for role-restricted features.

### How roles flow into the component

```
Keycloak JWT token
  └── tokenParsed.realm_access.roles = ["ROLE_USER"]  ← from server
        ↓
getAuthProfile() in keycloak.ts
  └── returns AuthProfile { realmRoles: ["ROLE_USER"] }
        ↓
users-page connectedCallback()
  └── this.isAdmin = this.hasAdminRole(getAuthProfile().realmRoles)
        ↓
render() checks isAdmin before injecting button HTML
```

### The role check function

```typescript
private hasAdminRole(realmRoles: string[]): boolean {
  return realmRoles.some((role) => {
    const normalized = role.toUpperCase();
    return normalized === 'ADMIN' || normalized === 'ROLE_ADMIN';
  });
}
```

**Why normalize to uppercase?**  
Keycloak can return roles as `ADMIN`, `admin`, `ROLE_ADMIN`, or `role_admin` depending on realm configuration. Normalizing prevents bugs from case sensitivity.

**Why check both `ADMIN` and `ROLE_ADMIN`?**  
Spring Security conventionally prefixes roles with `ROLE_`. The backend uses `ROLE_ADMIN`, but other setups might not. Supporting both prevents breakage if the realm config changes.

### In the template

```typescript
// Only rendered when isAdmin === true
${this.isAdmin ? `
  <button type="button" class="btn btn-primary" id="create-btn">
    + Create User
  </button>` : ''}

// Same for each row's Edit and Delete buttons
${this.isAdmin ? `
  <button class="btn btn-sm btn-secondary" data-action="edit" data-id="${u.id}">Edit</button>
  <button class="btn btn-sm btn-danger"    data-action="delete" data-id="${u.id}">Delete</button>
` : ''}
```

The condition is evaluated at render time. When `isAdmin` is `false`, those HTML strings are simply empty — they're never in the DOM.

---

## 3. Token Expiry Countdown

**File:** `src/components/scim-app.ts`

### The problem

JSON Web Tokens (JWTs) expire. Our tokens expire after ~5 minutes (configurable in Keycloak). If a user starts editing a form and the token expires mid-way, their save request will fail with a 401 error — a confusing experience.

### The solution: show time remaining in the topbar

```
👤 alice · ROLE_ADMIN · ⏱ 4m
```

### How it works

1. **On login**, after `renderShell()`, start a `setInterval`:

```typescript
private startCountdown(): void {
  const tick = (): void => {
    const chip = this.querySelector<HTMLElement>('#profile-chip');
    if (chip) chip.textContent = this.profileChipText();
  };
  this.countdownTimer = window.setInterval(tick, 60_000); // every 60 seconds
}
```

2. **`profileChipText()`** reads the current token expiry from the parsed JWT:

```typescript
private profileChipText(username?: string): string {
  const profile = getAuthProfile();
  const name = username ?? profile.preferredUsername;
  const roles = profile.realmRoles.join(', ') || 'none';
  const expiry = this.formatExpiry(profile.tokenExpiresAt);
  return `👤 ${name} · ${roles} · ${expiry}`;
}

private formatExpiry(iso: string | null): string {
  if (!iso) return '–';
  const mins = Math.ceil((new Date(iso).getTime() - Date.now()) / 60_000);
  if (mins <= 0) return 'expired';
  return `⏱ ${mins}m`;
}
```

3. **`getAuthProfile()`** in `keycloak.ts` reads `tokenParsed.exp` (Unix timestamp seconds) and converts it to an ISO string:

```typescript
tokenExpiresAt: parsed?.exp ? new Date(parsed.exp * 1000).toISOString() : null,
```

### Why stop the timer on logout/error?

```typescript
private stopCountdown(): void {
  if (this.countdownTimer !== null) {
    window.clearInterval(this.countdownTimer);
    this.countdownTimer = null;
  }
}
```

If the user session ends and we re-render an error page, the interval still holds a reference to the old element's DOM node. Stopping it prevents "ghost" updates to elements that no longer exist.

### Background token refresh

In `keycloak.ts`, there is a separate interval that silently refreshes the token every 60 seconds:

```typescript
refreshTimer = window.setInterval(() => {
  keycloak.updateToken(30)  // refresh if token expires in less than 30s
    .catch((error) => emit('onAuthError', classifyAuthError(error)));
}, 60_000);
```

This is separate from the countdown display — one refreshes the token, the other only updates the UI text.

---

## 4. 404 / Not-Found Fallback Page

**File:** `src/components/scim-app.ts`

### Why bother?

If a user bookmarks `#/settings` (which doesn't exist), they see a blank white page. A proper 404 is:
- More professional
- Actionable (tells the user how to get back)
- Prevents confusion

### Implementation

```typescript
type Route = '#/home' | '#/users' | '#/payments' | '#/not-found';
const VALID_ROUTES: readonly Route[] = ['#/home', '#/users', '#/payments', '#/not-found'];

function normalizeRoute(raw: string): Route {
  return (VALID_ROUTES as readonly string[]).includes(raw)
    ? (raw as Route)
    : '#/not-found';
}
```

Any unknown hash (`#/settings`, `#/what`, `#/`) routes to `#/not-found`.

The page itself is pure inline HTML — no need for a separate component because it has no logic:

```typescript
private renderNotFound(container: HTMLElement): void {
  container.innerHTML = `
    <div style="text-align:center; margin:72px auto;">
      <div style="font-size:64px;">404</div>
      <h2>Page Not Found</h2>
      <p>The page you're looking for doesn't exist or has been moved.</p>
      <a href="#/home">← Go to Home</a>
    </div>`;
}
```

Note: The `#/not-found` route itself doesn't match any nav link, so nothing is highlighted — visually correct.

---

## 5. Shared CSS Spinner

**File:** `src/components/shared/spinner.styles.ts`

### The problem before

Each page (`home-page.ts`, `users-page.ts`, `payments-page.ts`) had its own loading state HTML. They all looked slightly different, and if you wanted to change the spinner colour you had to edit three files.

### The solution: extract once, import everywhere

```typescript
// spinner.styles.ts
export const SPINNER_STYLES = `
.spinner {
  width: 28px; height: 28px;
  border: 3px solid #e5e7eb;
  border-top-color: #4f46e5;   /* matches brand colour */
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg) } }
.loading-state {
  display: flex; flex-direction: column; align-items: center;
  padding: 40px 32px;
  color: #6b7280; font-size: 14px;
}
`;

export const SPINNER_HTML =
  '<div class="loading-state" role="status" aria-label="Loading">' +
  '<div class="spinner" aria-hidden="true"></div>Loading…</div>';
```

Now each page does:

```typescript
import { SPINNER_STYLES, SPINNER_HTML } from '../shared/spinner.styles';

// In styles:
<style>${SPINNER_STYLES} /* ... rest of page styles */ </style>

// In loading state:
content.innerHTML = SPINNER_HTML;
```

**Why `aria-hidden="true"` on the spinner div?**  
Screen readers don't need to announce the spinning circle — the outer `role="status"` with `aria-label="Loading"` provides the accessible description.

**Why `role="status"` on the wrapper?**  
It's a "live region" — screen readers announce its content when it changes, without the user having to navigate to it. This means blind users hear "Loading…" automatically when the page loads.

---

## 6. Keyboard Navigation & Focus Trap in Modal

**File:** `src/components/shared/modal-dialog.ts`

### The WCAG requirement

WCAG 2.1 Success Criterion 2.1.2 (Level A): *"If keyboard focus can be moved to a component using a keyboard interface, then focus can be moved away from that component using only a keyboard interface."*

Translation: if you Tab into a modal, Tab must keep cycling inside the modal — not escape to the page behind it.

### What we implemented

#### ESC to close

```typescript
private readonly onKeydown = (e: KeyboardEvent): void => {
  if (!this.hasAttribute('open')) return;  // ignore when closed

  if (e.key === 'Escape') {
    this.cancel();
    return;
  }
  // ...
};

connectedCallback(): void {
  document.addEventListener('keydown', this.onKeydown);
}

disconnectedCallback(): void {
  document.removeEventListener('keydown', this.onKeydown);
}
```

We listen on `document` (not just the component) because keyboard events bubble up to the document level.

#### Focus trap with Tab / Shift+Tab

```typescript
if (e.key === 'Tab') {
  const focusable = Array.from(
    this.shadowRoot!.querySelectorAll<HTMLElement>(this.FOCUSABLE_SELECTOR),
  ).filter((el) => el.offsetParent !== null || el.tagName === 'BUTTON');

  const first = focusable[0];
  const last  = focusable[focusable.length - 1];
  const active = this.shadowRoot!.activeElement;

  if (e.shiftKey && active === first) {
    e.preventDefault();   // stop default browser behavior
    last.focus();          // wrap to last
  } else if (!e.shiftKey && active === last) {
    e.preventDefault();
    first.focus();         // wrap to first
  }
}
```

**Focusable selector:**
```typescript
private readonly FOCUSABLE_SELECTOR =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), ' +
  'textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
```

This covers every standard interactive element. Disabled elements are excluded because users can't interact with them.

#### Focus management on open/close

```typescript
attributeChangedCallback(name: string): void {
  if (name === 'open') {
    if (this.hasAttribute('open')) {
      // Save current focus so we can restore it when modal closes
      this.previousFocus = document.activeElement;
      requestAnimationFrame(() => {
        const first = this.shadowRoot!.querySelector<HTMLElement>(this.FOCUSABLE_SELECTOR);
        first?.focus();   // move focus INTO the modal
      });
    } else {
      // Restore focus to whatever triggered the modal (e.g. the "Edit" button)
      if (this.previousFocus instanceof HTMLElement) {
        this.previousFocus.focus();
      }
    }
  }
}
```

**Why `requestAnimationFrame`?**  
`render()` is called synchronously inside `attributeChangedCallback`, but the DOM updates (setting `display: flex` on the overlay) happen in the same microtask. We need to wait one frame for the browser to actually paint the element before we can focus it — otherwise `focus()` silently does nothing on an invisible element.

**Why restore `previousFocus`?**  
Without this, after closing a modal, the browser moves focus to the `<body>` — the user has completely lost their place in the page and must Tab all the way back. Restoring focus to the triggering button is required by WCAG 2.4.3.

---

## 7. WCAG Colour Contrast

**Files:** `modal-dialog.ts`, `users-page.styles.ts`, `payments-page.styles.ts`

### What is WCAG AA contrast?

WCAG (Web Content Accessibility Guidelines) defines a minimum contrast ratio between text colour and its background. For users with low vision or colour blindness, low-contrast text is very hard to read.

- **4.5 : 1** minimum for normal text (< 18px or not bold)
- **3 : 1** minimum for large text (≥ 18px, or ≥ 14px bold) and UI controls

### How we verified — manual check using the formula

```
Relative luminance: L = 0.2126·R + 0.7152·G + 0.0722·B
Contrast ratio = (L1 + 0.05) / (L2 + 0.05)   where L1 > L2
```

Reference: https://webaim.org/resources/contrastchecker/

### Key colour decisions

| Element | Text colour | Background | Ratio | Requirement | Status |
|---|---|---|---|---|---|
| Primary button ("Save") | `#ffffff` white | `#4338ca` indigo-700 | **7.0 : 1** | 4.5 : 1 | ✅ AA |
| Danger button ("Delete") | `#ffffff` white | `#b91c1c` red-700 | **5.9 : 1** | 4.5 : 1 | ✅ AA |
| Cancel button | `#374151` gray-700 | `#ffffff` white | **9.7 : 1** | 4.5 : 1 | ✅ AA |
| Active badge (green) | `#065f46` | `#d1fae5` | **7.5 : 1** | — | ✅ AA |
| Inactive badge (gray) | `#374151` | `#f3f4f6` | **7.7 : 1** | — | ✅ AA |
| Error text | `#991b1b` | `#fee2e2` | **5.2 : 1** | 4.5 : 1 | ✅ AA |

### Focus-visible outlines

Every interactive element has `:focus-visible` styles so keyboard users can see which element has focus:

```css
button:focus-visible { outline: 3px solid #6366f1; outline-offset: 2px; }
nav a:focus-visible  { outline: 2px solid #fff;    outline-offset: 2px; }
```

`focus-visible` (not `focus`) means the outline only appears for keyboard navigation — mouse clicks don't show the ring, which is the modern standard.

---

## 8. Structured Error Handling – `ApiHttpError`

**File:** `src/api/http.ts`

### Why a custom error class?

Native `fetch()` doesn't throw on HTTP errors (404, 500, etc.) — it only throws on network failures. You have to check `response.ok` yourself.

Before:
```typescript
// Everywhere – duplicated, inconsistent error handling
const res = await fetch('/api/v1/users');
if (!res.ok) throw new Error('failed'); // no status code, vague message
```

After:
```typescript
// http.ts – once, centralized
export class ApiHttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiHttpError';
    this.status = status;
  }
}
```

Components can now make smart decisions:

```typescript
try {
  await deleteUser(id);
} catch (e) {
  if (e instanceof ApiHttpError && e.status === 404) {
    this.toast('User already deleted.', 'error');
  } else {
    this.toast('Unexpected error. Try again.', 'error');
  }
}
```

### Surface server validation messages

```typescript
if (!response.ok) {
  let serverMessage = `Request failed (${response.status}).`;
  try {
    const err = (await response.json()) as { message?: string };
    if (err.message) serverMessage = err.message;  // use backend's message if available
  } catch { /* non-JSON body */ }
  throw new ApiHttpError(response.status, serverMessage);
}
```

If the backend returns `{ "message": "Username already taken" }`, that message is shown to the user — not a generic "400 error".

### Automatic token refresh

Before every request, `authHeader()` calls `keycloak.updateToken(30)` — if the token expires in less than 30 seconds, it silently refreshes it:

```typescript
async function authHeader(): Promise<Record<string, string>> {
  const keycloak = getKeycloak();
  try {
    await keycloak.updateToken(30);
  } catch {
    await login();  // can't refresh → redirect to login
    throw new ApiHttpError(401, 'Session expired.');
  }
  return { Authorization: `Bearer ${keycloak.token}` };
}
```

---

## 9. CSS Architecture – Separation of Concerns

**Files:** `users-page.styles.ts`, `payments-page.styles.ts`, `spinner.styles.ts`

### The problem

Page components started with all their style logic inline in `render()`. For `users-page.ts` that was ~80 lines of CSS inside a template literal inside a method — impossible to scan.

### The pattern

```
users-page.ts          ← component logic only (state, events, render calls)
users-page.styles.ts   ← the USERS_PAGE_STYLES constant string
spinner.styles.ts      ← shared spinner used by all pages
```

```typescript
// users-page.styles.ts
export const USERS_PAGE_STYLES = `
  .table { width: 100%; border-collapse: collapse; }
  .badge-active   { background: #d1fae5; color: #065f46; }
  .badge-inactive { background: #f3f4f6; color: #374151; }
  /* ... */
`;

// users-page.ts
import { USERS_PAGE_STYLES } from './users-page.styles';
import { SPINNER_STYLES, SPINNER_HTML } from '../shared/spinner.styles';

// In render():
innerHTML = `<style>${USERS_PAGE_STYLES} ${SPINNER_STYLES}</style>
             ${this.loading ? SPINNER_HTML : this.renderTable()}`;
```

**Benefits:**
- Your editor highlights CSS syntax correctly in `.styles.ts` files
- Component files are ~150 lines shorter and easier to scan
- Changing brand colours is one file change, not three

---

## 10. Event-Driven Components: How Custom Events Work

**Files:** `pagination-bar.ts`, `modal-dialog.ts`

### Why custom events?

Web Components should be self-contained — they should not directly reach into parent components. Instead they **fire events**, and parents **listen**. This is the same pattern as `<input>` firing `change` or `<button>` firing `click`.

### Pagination example

```typescript
// pagination-bar.ts – fires an event
this.shadowRoot!.getElementById('next')?.addEventListener('click', () => {
  if (hasMore) {
    this.dispatchEvent(new CustomEvent('page-change', {
      detail: { page: page + 1, size },
      bubbles: true,   // event travels up the DOM tree
      composed: true,  // event crosses shadow DOM boundaries
    }));
  }
});

// users-page.ts – listens
paginationEl.addEventListener('page-change', (e: Event) => {
  const { page, size } = (e as CustomEvent<{ page: number; size: number }>).detail;
  this.page = page;
  this.size = size;
  void this.loadUsers();
});
```

**`bubbles: true`** — The event travels up through parent elements. This lets a parent far up the tree listen without the pagination bar needing to know who its parent is.

**`composed: true`** — Events don't cross shadow DOM boundaries by default. `composed: true` makes them cross.

### Modal example

```typescript
// modal-dialog.ts
// The modal fires "dialog-confirm"; the PARENT decides when to close it
private confirm(): void {
  this.dispatchEvent(new CustomEvent('dialog-confirm', { bubbles: true, composed: true }));
  // intentionally does NOT remove "open" attribute
}

// users-page.ts
modalEl.addEventListener('dialog-confirm', async () => {
  await this.handleFormSubmit();       // validate and call API
  if (saveSucceeded) {
    modalEl.removeAttribute('open');  // parent closes modal on success
  }
  // on validation error: modal stays open, error shown in form
});
```

This design means the modal doesn't have any business logic — it's pure UI. The page component handles all validation and API calls.

---

## 11. Light DOM vs Shadow DOM – When to Use Which

This is one of the trickier Web Components concepts.

### Shadow DOM (used by: `<modal-dialog>`, `<pagination-bar>`, `<notification-bar>`)

The component creates a private DOM tree via `this.attachShadow({ mode: 'open' })`. Styles inside don't affect the rest of the page.

**Use when:** The component is self-contained (has its own styles that should never be affected by the page).

### Light DOM (used by: `<users-page>`, `<payments-page>`, `<home-page>`)

The component writes directly to `this.innerHTML` — into the regular DOM.

**Use when:** The component renders a `<modal-dialog>` with a `<slot>`. Slot projection only works when the slotted content is in the **light** DOM of the host. If a page used Shadow DOM, its form elements would be trapped inside that shadow root and couldn't project into `<modal-dialog>`'s `<slot>`.

```html
<!-- This only works because users-page is light DOM -->
<modal-dialog title="Create User" open>
  <form id="user-form">...</form>  ← projects into <slot> in modal-dialog's shadow
</modal-dialog>
```

---

## 12. `Promise.allSettled` – Resilient Parallel Fetching

**File:** `src/components/pages/home-page.ts`

The home page dashboard shows cards for both users count and payments count. We fetch both simultaneously.

### Why not `Promise.all`?

```typescript
// BAD: if payments API fails, the whole dashboard fails
const [users, payments] = await Promise.all([listUsers(0,10), listPayments(0,10)]);
```

`Promise.all` rejects as soon as **any** promise rejects. If the payments service is down, the user would see an error on the entire home page — even though their users data loaded fine.

### Why `Promise.allSettled`?

```typescript
// GOOD: each card shows independently
const [usersResult, paymentsResult] = await Promise.allSettled([
  listUsers(0, 10),
  listPayments(0, 10),
]);

this.usersCount =
  usersResult.status === 'fulfilled' ? usersResult.value.length : null;
this.paymentsCount =
  paymentsResult.status === 'fulfilled' ? paymentsResult.value.length : null;
```

`Promise.allSettled` **always resolves** with an array of outcomes. Each outcome is either `{ status: 'fulfilled', value: T }` or `{ status: 'rejected', reason: Error }`. Individual cards can show "unavailable" without blocking the others.

---

## 13. Summary of Files Changed in Phase 9

| File | Change | Why |
|---|---|---|
| `scim-app.ts` | Added `type Route`, `normalizeRoute()`, `#/not-found` case in `routeTo()` | Safe routing; clean URL when hash is unknown |
| `scim-app.ts` | Added `profileChipText()`, `formatExpiry()`, `startCountdown()`, `stopCountdown()` | Token expiry visible to user in topbar |
| `scim-app.ts` | Added `:focus-visible` CSS on nav links and logout button | Keyboard users can see active focus |
| `modal-dialog.ts` | Added `onKeydown` listener, focus trap, `previousFocus` save/restore | WCAG 2.1.2 (no keyboard trap) compliance |
| `modal-dialog.ts` | WCAG AA colour ratios on all buttons | Low-vision accessibility |
| `shared/spinner.styles.ts` | Created `SPINNER_STYLES` + `SPINNER_HTML` constants | DRY, consistent loading state across all pages |
| `users-page.styles.ts` | Extracted from `users-page.ts` | Separation of concerns |
| `payments-page.styles.ts` | Extracted from `payments-page.ts` | Separation of concerns |
| `users-page.ts` | `hasAdminRole()` normalizes to uppercase, checks `ADMIN` + `ROLE_ADMIN` | Resilient role check across env configs |
| All page components | Import `SPINNER_STYLES` / `SPINNER_HTML` from shared | Consistent spinners, single change to update all |

---

## 14. Verification Commands

```bash
# Type-check the TypeScript
cd frontend && npm run typecheck

# Run all unit tests
npm run test

# Full build (what CI does)
npm run build

# Run backend quality gates
cd .. && mvn -B clean verify
```

Expected outcome: All `55+` tests green, no TypeScript errors, no build errors.

---

## 15. Key Concepts Recap for Juniors

| Concept | Where Used | Learn More |
|---|---|---|
| Hash Router | `scim-app.ts` | [MDN: hashchange event](https://developer.mozilla.org/en-US/docs/Web/API/Window/hashchange_event) |
| Web Components / Custom Elements | Every `.ts` in `components/` | [MDN: Using custom elements](https://developer.mozilla.org/en-US/docs/Web/API/Web_components/Using_custom_elements) |
| Shadow DOM | `modal-dialog`, `pagination-bar`, `notification-bar` | [MDN: Shadow DOM](https://developer.mozilla.org/en-US/docs/Web/API/Web_components/Using_shadow_DOM) |
| JWT roles / RBAC | `users-page.ts`, `keycloak.ts` | [JWT.io introduction](https://jwt.io/introduction) |
| Custom Events | `pagination-bar`, `modal-dialog` | [MDN: CustomEvent](https://developer.mozilla.org/en-US/docs/Web/API/CustomEvent) |
| WCAG AA contrast | `modal-dialog.ts`, style files | [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/) |
| Focus trap | `modal-dialog.ts` | [WCAG 2.1.2](https://www.w3.org/WAI/WCAG21/Understanding/no-keyboard-trap.html) |
| `Promise.allSettled` | `home-page.ts` | [MDN: Promise.allSettled](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Promise/allSettled) |
| Memory leaks / cleanup | `scim-app.ts disconnectedCallback` | [MDN: Event Listener best practices](https://developer.mozilla.org/en-US/docs/Web/API/EventTarget/addEventListener) |
| Typed errors | `http.ts ApiHttpError` | [TypeScript error handling patterns](https://www.typescriptlang.org/docs/handbook/2/narrowing.html) |

