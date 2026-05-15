/**
 * Shared CSS loading spinner used across all page components.
 * Import SPINNER_STYLES into a page's stylesheet and use SPINNER_HTML
 * wherever a loading state should appear.
 */

export const SPINNER_STYLES = `
.spinner{
  width:28px;height:28px;
  border:3px solid #e5e7eb;
  border-top-color:#4f46e5;
  border-radius:50%;
  animation:spin .7s linear infinite;
  margin:0 auto 10px;
}
@keyframes spin{to{transform:rotate(360deg)}}
.loading-state{
  display:flex;flex-direction:column;align-items:center;
  padding:40px 32px;
  color:#6b7280;
  font-family:system-ui,sans-serif;
  font-size:14px;
}
`;

/** Drop-in loading HTML that renders the CSS spinner. */
export const SPINNER_HTML =
  '<div class="loading-state" role="status" aria-label="Loading">' +
  '<div class="spinner" aria-hidden="true"></div>Loading\u2026</div>';

