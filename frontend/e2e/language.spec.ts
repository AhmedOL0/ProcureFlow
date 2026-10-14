import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';

/**
 * Language gate: stored preference applies before first paint
 * (<html lang>), the shell menu flips and persists it, and the French
 * login plus an authenticated French dashboard are axe-clean (same
 * doubles shape as theme.spec.ts — no live backend).
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

async function signIn(page: Page): Promise<void> {
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

test('stored French preference applies before first paint', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('pf-lang', 'fr'));
  await page.goto('/login');
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible();
});

test('shell menu flips and persists the language', async ({ page }) => {
  await signIn(page);
  await expect(page.getByText('Procurement overview')).toBeVisible();
  await page.getByRole('button', { name: 'Language' }).click();
  await page.getByRole('menuitem', { name: 'French' }).click();
  await expect(page.getByText('Aperçu des achats')).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
  await expect(page.getByText('Aperçu des achats')).toBeVisible();
  await page.getByRole('button', { name: 'Langue' }).click();
  await page.getByRole('menuitem', { name: 'Anglais' }).click();
  await expect(page.getByText('Procurement overview')).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
});

test('French login has no axe violations', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('pf-lang', 'fr'));
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Se connecter' })).toBeVisible();
  await expectAxeClean(page);
});

test('French dashboard has no axe violations', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('pf-lang', 'fr'));
  const token = fakeJwt(['tenant:admin']);
  await stubApi(page, token);
  await page.goto('/login');
  await page.getByLabel('E-mail professionnel').fill('boss@acme.test');
  await page.getByLabel('Mot de passe', { exact: true }).fill('x');
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });
  await expect(page.getByText('Aperçu des achats')).toBeVisible();
  await expectAxeClean(page);
});
