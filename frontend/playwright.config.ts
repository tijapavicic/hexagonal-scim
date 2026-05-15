import { defineConfig } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL ?? 'https://localhost:3000';

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL,
    ignoreHTTPSErrors: true,
    trace: 'on-first-retry',
  },
  reporter: 'list',
});

