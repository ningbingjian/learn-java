import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 45000,
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: { browserName: 'chromium', headless: true, trace: 'retain-on-failure' },
});
