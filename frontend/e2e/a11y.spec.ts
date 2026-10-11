import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';

/**
 * Accessibility gate: axe-core over the login screen plus four
 * authenticated screens with deterministic API doubles (same stub shape
 * as journeys.spec.ts — no live backend). Any violation fails the run;
 * triage by fixing markup, never by disabling rules.
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
    } else if (method === 'GET' && url.includes('/auth/me')) {
      await json(ME);
    } else if (method === 'GET' && url.includes('/users/me')) {
      await json(ME);
    } else if (method === 'GET' && url.includes('/purchase-requests')) {
      await json([]);
    } else if (method === 'GET' && url.includes('/suppliers')) {
      await json([]);
    } else if (method === 'GET' && url.includes('/orders')) {
      await json([]);
    } else if (method === 'GET' && url.includes('/supplier-categories')) {
      await json([]);
    } else if (method === 'GET' && url.includes('/notifications')) {
      await json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    } else {
      await route.abort('failed');
    }
  });
}

async function signIn(page: Page): Promise<void> {
  // Limited authorities: the dashboard renders its operations pulse only,
  // so the stub surface stays small and deterministic.
  const token = fakeJwt(['procurement:request']);
  await stubApi(page, token);
  await page.goto('/login');
  await page.getByLabel('Work email').fill('boss@acme.test');
  await page.getByLabel('Password').fill('x');
  await page.getByRole('button', { name: 'Sign in' }).click();
  // Generous timeout: full-matrix runs parallelize two browsers and the
  // dev server occasionally needs more than the 5s default to respond.
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 15000 });
}

async function expectAxeClean(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze();
  expect(results.violations).toEqual([]);
}

test.describe('accessibility', () => {
  test('login has no axe violations', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
    await expectAxeClean(page);
  });

  test('dashboard has no axe violations', async ({ page }) => {
    await signIn(page);
    await expect(page.getByText('Procurement overview')).toBeVisible();
    await expectAxeClean(page);
  });

  test('suppliers has no axe violations', async ({ page }) => {
    await signIn(page);
    await page.goto('/suppliers');
    await expect(page.getByText('Supplier directory')).toBeVisible();
    await expectAxeClean(page);
  });

  test('requests has no axe violations', async ({ page }) => {
    await signIn(page);
    await page.goto('/requests');
    await expect(page.getByRole('heading', { name: 'Purchase requests' })).toBeVisible();
    await expectAxeClean(page);
  });

  test('settings has no axe violations', async ({ page }) => {
    await signIn(page);
    await page.goto('/settings');
    await expect(page.getByText('Account settings')).toBeVisible();
    await expectAxeClean(page);
  });
});
