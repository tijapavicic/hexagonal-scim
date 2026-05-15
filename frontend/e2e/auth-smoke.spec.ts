import { test, expect } from '@playwright/test';

const username = process.env.E2E_USERNAME ?? 'testuser';
const password = process.env.E2E_PASSWORD ?? 'password';

test('login redirect and protected users API smoke path', async ({ page, baseURL }) => {
  const apiResponsePromise = page.waitForResponse(
    (response) => response.url().includes('/api/v1/users') && response.request().method() === 'GET',
    { timeout: 30_000 },
  );

  await page.goto('/');

  // App should redirect to Keycloak login when unauthenticated.
  await expect(page).toHaveURL(/\/realms\/hexagonal-scim\//);

  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();

  // After login user returns to the app shell.
  await expect(page).toHaveURL(new RegExp(`^${(baseURL ?? '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`));
  await expect(page.getByText('Signed in as')).toBeVisible();

  // API E2E path: users endpoint should succeed post-login.
  const apiResponse = await apiResponsePromise;
  expect(apiResponse.status()).toBe(200);

  await expect(page.getByRole('heading', { name: 'Users' })).toBeVisible();
});

