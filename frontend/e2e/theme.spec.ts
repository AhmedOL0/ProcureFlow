import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';

/**
 * Theme gate: stored preference applies before first paint, the shell
 * toggle flips and persists it, and the dark theme is axe-clean on the
 * login screen plus an authenticated dashboard (same doubles shape as
 * a11y.spec.ts — no live backend).
 */

function fakeJwt(authorities: string[]): string {
  const b64 = (value: object): string => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${b64({ alg: 'HS512' })}.${b64({ authorities })}.sig`;
}

const ME = {
  id: 'u-admin',
  email: 'boss@acme.test',
  firstName: '',
  lastName: '',
  status: 'ACTIVE',
  tenantSlug: 'acme',
  roles: ['TENANT_ADMIN'],
};

async function stubApi(page: Page, token: string): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = request.url();
    const method = request.method();
    const json = (body: unknown, status = 200): Promise<void> =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (method === 'POST' && url.includes('/auth/login')) {
      await json({ accessToken: token, refreshToken: 'r', expiresInSeconds: 900, user: ME });
    } else if (method === 'GET' && (url.includes('/auth/me') || url.includes('/users/me'))) {
      await json(ME);
    } else if (method === 'GET' && url.includes('/purchase-requests')) {
      await json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    } else if (method === 'GET' && url.includes('/notifications')) {
      await json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    } else {
      await route.abort('failed');
    }
  });
}

async function signInDark(page: Page): Promise<void> {
  await page.addInitScript(() => localStorage.setItem('pf-theme', 'dark'));
  const token = fakeJwt(['tenant:admin']);
  await stubApi(page, token);
  await page.goto('/login');
  await page.getByLabel('Work email').fill('boss@acme.test');
  await page.getByLabel('Password', { exact: true }).fill('x');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });
}

async function expectAxeClean(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
  expect(results.violations).toEqual([]);
}

test('stored dark preference applies before first paint', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('pf-theme', 'dark'));
  await page.goto('/login');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});

test('shell toggle flips and persists the theme', async ({ page }) => {
  const token = fakeJwt(['tenant:admin']);
  await stubApi(page, token);
  await page.goto('/login');
  await page.getByLabel('Work email').fill('boss@acme.test');
  await page.getByLabel('Password', { exact: true }).fill('x');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });
  await expect(page.locator('html')).not.toHaveAttribute('data-theme', 'dark');
  await page.getByRole('button', { name: 'Switch to dark mode' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect(page.getByRole('button', { name: 'Switch to light mode' })).toBeVisible();
});

test('dark login has no axe violations', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('pf-theme', 'dark'));
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
  await expectAxeClean(page);
});

test('dark dashboard has no axe violations', async ({ page }) => {
  await signInDark(page);
  await expect(page.getByText('Procurement overview')).toBeVisible();
  await expectAxeClean(page);
});
