import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  retries: process.env['CI'] ? 2 : 0,
  // Artifacts (traces, error context) default into test-results/, but the
  // dev server watches the project root — a failing run's own artifacts
  // trigger a reload that poisons retries. Point PW_OUTPUT outside the
  // tree for live debugging sessions.
  outputDir: process.env['PW_OUTPUT'] ?? 'test-results',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run start -- --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env['CI'],
    timeout: 180 * 1000,
  },
});
